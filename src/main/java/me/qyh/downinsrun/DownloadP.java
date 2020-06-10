package me.qyh.downinsrun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.qyh.downinsrun.parser.ParseUtils;
import org.apache.http.impl.client.CloseableHttpClient;

import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.Https;
import me.qyh.downinsrun.parser.Https.DownloadProgressNotify;
import me.qyh.downinsrun.parser.InsParser;
import me.qyh.downinsrun.parser.InsParser.Url;

/**
 * 下载某一个帖子
 * 
 * @author wwwqyhme
 *
 */
class DownloadP {

	private final String code;
	private final Path errorF;
	private final Path root;
	private final Path tempDir;
	private final CloseableHttpClient client = Https.newHttpClient();
	private final InsParser parser = new InsParser(false, client);

	private DownloadProgressNotify notify;

	public DownloadP(String code, Path dir) throws Exception {
		super();
		ParseUtils.trySetSid(client);
		this.code = code;
		this.root = dir.resolve(code);
		try {
			Files.createDirectories(root);
		} catch (Throwable e) {
			System.out.println("创建文件夹:" + root + "失败");
			System.exit(-1);
		}
		this.tempDir = root.resolve("temp");
		try {
			Files.createDirectories(tempDir);
		} catch (Exception e) {
			System.out.println("创建文件夹:" + tempDir + "失败");
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
	}

	public void start() {
		// 下载失败文件
		DownloadF.downloadError(parser, client, errorF, tempDir);

		List<Url> urls = new ArrayList<>();
		boolean error = false;
		try {
			urls = parser.parsePost(code).getUrls();
		} catch (LogicException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		} catch (Throwable e) {
			error = true;
		}
		if (!error) {
			error = urls.isEmpty();
		}
		if (error) {
			System.out.println("获取帖子内容失败");
			System.exit(-1);
		}
		ExecutorService es = Executors
				.newFixedThreadPool(Math.min(Configure.get().getConfig().getThreadNum(), urls.size()));
		int index = 0;
		for (Url url : urls) {
			final int _index = index;
			String name = code + "_" + (++index) + "." + Utils.getFileExtension(url.getValue());
			es.execute(() -> {
				DownloadF df = new DownloadF(errorF, url.getValue(), root.resolve(name), client, tempDir, code, _index,
						notify);
				df.start();
			});
		}
		es.shutdown();
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println("下载帖子" + code + "失败");
			System.exit(-1);
		}

		try {
			String errorFContent = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
			if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
				Files.delete(errorF);
			}
		} catch (Throwable e) {
		}

		DownloadF.downloadError(parser, client, errorF, tempDir);

		try {
			String errorFContent = new String(Files.readAllBytes(errorF));
			if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
				Files.delete(errorF);
			}
		} catch (Throwable e) {
		}
		try {
			client.close();
		} catch (IOException e) {
		}
		Utils.deleteDir(tempDir);
		System.out.println("下载帖子" + code + "完成，文件存储目录:" + root.toString());
	}

	public void setNotify(DownloadProgressNotify notify) {
		this.notify = notify;
	}

}
