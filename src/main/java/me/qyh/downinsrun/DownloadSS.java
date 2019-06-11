package me.qyh.downinsrun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.impl.client.CloseableHttpClient;

import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.Https;
import me.qyh.downinsrun.parser.InsParser;
import me.qyh.downinsrun.parser.InsParser.Url;
import me.qyh.downinsrun.parser.Story;
import me.qyh.downinsrun.parser.UserParser;

/**
 * 用户所有story 下载
 * 
 * @author wwwqyhme
 *
 */
class DownloadSS {

	private final String user;
	private final Path root;
	private final Path errorF;
	private final ExecutorService es = Executors.newFixedThreadPool(Configure.get().getConfig().getThreadNum());
	private final CloseableHttpClient client = Https.newHttpClient();
	private final InsParser parser = new InsParser(false, client);
	private final Path destDir;
	private final Path tempDir;

	public DownloadSS(String user, Path dir) {
		super();
		this.user = user;
		this.root = dir.resolve(user + "_story");
		try {
			Files.createDirectories(root);
		} catch (Throwable e) {
			System.out.println("创建文件夹:" + root + "失败");
			System.exit(-1);
		}

		this.tempDir = root.resolve("temp");
		Utils.deleteDir(tempDir);

		try {
			Files.createDirectories(tempDir);
		} catch (Throwable e) {
			System.out.println("创建文件:" + tempDir + "失败");
			System.exit(-1);
		}

		this.errorF = root.resolve("error_f.json");
		if (!Files.exists(errorF)) {
			try {
				Files.createFile(errorF);
			} catch (Throwable e) {
				System.out.println("创建文件:" + errorF + "失败");
				System.exit(-1);
			}
		}

		destDir = root;
	}

	public void start() {
		// 下载失败文件
		DownloadF.downloadError(parser, client, errorF, tempDir);
		execute();

		es.shutdown();
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println("下载用户" + user + "stories失败");
			System.exit(-1);
		}

		DownloadF.downloadError(parser, client, errorF, tempDir);

		try {
			String errorFContent = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
			if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
				Files.delete(errorF);
			}
		} catch (Throwable e) {
		}

		try {
			client.close();
		} catch (IOException e) {
		}

		if (Files.exists(errorF)) {
			System.out.println("存在下载失败的文件，请重新运行命令以下载失败的文件");
		}
		System.out.println("下载用户" + user + "story完成，文件存储目录:" + destDir.toString());

	}

	/**
	 * 
	 */
	private void execute() {
		UserParser tp = null;
		try {
			tp = parser.newUserParser(this.user, false);
		} catch (LogicException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("初始化用户解析器失败");
			System.exit(-1);
		}
		Map<String, List<Url>> map = new HashMap<>();
		try {
			List<Story> stories = tp.stories();
			map = parser.parseStory(stories.stream().map(Story::getId).toArray(String[]::new));
		} catch (LogicException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}

		for (Map.Entry<String, List<Url>> it : map.entrySet()) {
			String id = it.getKey();

			int index = 0;
			for (Url curl : it.getValue()) {
				String ext = Utils.getFileExtension(curl.getValue());
				String name = (++index) + "." + ext;
				Path dest = destDir.resolve(id).resolve(name);
				if (!Files.exists(dest.getParent())) {
					synchronized (this) {
						if (!Files.exists(dest.getParent())) {
							try {
								Files.createDirectories(dest.getParent());
							} catch (IOException e) {
								System.out.println("创建文件夹:" + dest.getParent() + "失败");
								System.exit(-1);
							}
						}
					}
				}
				try {
					es.execute(() -> {
						DownloadF df = new DownloadF(errorF, curl.getValue(), dest, client, tempDir, null, -1);
						df.start();
					});
				} catch (Throwable e) {
					DownloadF.logError(errorF, dest, curl.getValue(), null, -1);
				}
			}
		}
	}

}
