package me.qyh.downinsrun;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.Https;
import me.qyh.downinsrun.parser.InsParser;
import me.qyh.downinsrun.parser.InsParser.Url;
import me.qyh.downinsrun.parser.PagingItem;
import me.qyh.downinsrun.parser.PagingResult;
import me.qyh.downinsrun.parser.TagParser;

/**
 * 标签下载
 * 
 * @author wwwqyhme
 *
 */
class DownloadT {

	private final String tag;
	private final Path root;
	private final Path errorF;
	private final Path errorP;
	private final Path posts;
	private final Set<String> postSet = new HashSet<>();
	private final Set<String> increasePostSet = new HashSet<>();
	private final ExecutorService es = Executors.newFixedThreadPool(Configure.get().getConfig().getThreadNum());
	private final ExecutorService es2 = Executors.newFixedThreadPool(Configure.get().getConfig().getThreadNum());
	private final Object lock = new Object();
	private final CloseableHttpClient client = Https.newHttpClient();
	private final InsParser parser = new InsParser(false, client);
	private final int first = 50;
	private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final String END_PREFIX = "========================end ";
	private final boolean increaseDownload;// 增量下载
	private final Path destDir;
	private final Path tempDir;
	private final Integer maxInDir;
	private boolean writeP;
	private volatile boolean downloaded;
	private ExecutorService moveExecutor;

	public DownloadT(String tag, Path dir, Integer maxInDir) {
		super();
		this.tag = tag;
		this.root = dir.resolve(tag);
		this.maxInDir = maxInDir;
		if (maxInDir < 1) {
			System.out.println("文件夹内存放文件最大数目不能小于1");
			System.exit(-1);
		}
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

		this.posts = root.resolve("posts.txt");

		if (!Files.exists(posts)) {
			try {
				Files.createFile(posts);
			} catch (Throwable e) {
				System.out.println("创建文件:" + posts + "失败");
				System.exit(-1);
			}
		}
		String[] postsArray;
		try {
			postsArray = Files.readAllLines(posts).stream().filter(s -> !s.isEmpty()).toArray(String[]::new);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		int lastEndIndex = -1;
		for (int i = 0; i < postsArray.length; i++) {
			String post = postsArray[i];
			if (post.startsWith(END_PREFIX)) {
				lastEndIndex = i;
			}
		}

		increaseDownload = lastEndIndex != -1;

		if (increaseDownload) {
			for (int i = 0; i < lastEndIndex; i++) {
				String post = postsArray[i];
				if (!post.startsWith(END_PREFIX)) {
					postSet.add(post);
				}
			}
			for (int i = postsArray.length - 1; i > lastEndIndex; i--) {
				increasePostSet.add(postsArray[i]);
			}
		} else {
			for (String post : postsArray) {
				postSet.add(post);
			}
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

		this.errorP = root.resolve("error_p.json");
		if (!Files.exists(errorP)) {
			try {
				Files.createFile(errorP);
			} catch (Throwable e) {
				System.out.println("创建文件:" + errorP + "失败");
				System.exit(-1);
			}
		}
		destDir = root;
	}

	public void start() {
		if (maxInDir < Integer.MAX_VALUE) {
			moveExecutor = Executors.newSingleThreadExecutor();
			moveExecutor.execute(new DirFilesMoveMonitor(destDir));
		}

		// 下载失败帖子
		downloadError();
		// 下载失败文件
		DownloadF.downloadError(parser, client, errorF, tempDir);
		execute("");

		es2.shutdown();
		try {
			es2.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println("下载标签" + tag + "帖子失败");
			System.exit(-1);
		}

		es.shutdown();
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.out.println("下载标签" + tag + "帖子失败");
			System.exit(-1);
		}
		downloadError();
		DownloadF.downloadError(parser, client, errorF, tempDir);

		try {
			String errorFContent = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
			if (errorFContent.isEmpty() || errorFContent.equals("[]")) {
				Files.delete(errorF);
			}
		} catch (Throwable e) {
		}

		try {
			String errorPContent = new String(Files.readAllBytes(errorP));
			if (errorPContent.isEmpty() || errorPContent.equals("[]")) {
				Files.delete(errorP);
			}
		} catch (Throwable e) {
		}

		try {
			client.close();
		} catch (IOException e) {
		}

		if (writeP) {
			try (Writer writer = Files.newBufferedWriter(posts, StandardOpenOption.APPEND)) {
				writer.write(END_PREFIX + dtf.format(LocalDateTime.now()));
				writer.write(System.lineSeparator());
			} catch (Exception e) {
			}
		} else {
			try {
				Files.deleteIfExists(destDir);
			} catch (Exception e) {
			}
		}

		if (Files.exists(errorF) || Files.exists(errorP)) {
			System.out.println("存在下载失败的文件或帖子，请重新运行命令以下载失败的文件");
		}
		Utils.deleteDir(tempDir);
		if (increaseDownload) {
			if (writeP) {
				System.out.println("增量下载标签" + tag + "完成，文件存储目录:" + destDir.toString());
			} else {
				System.out.println("增量下载标签" + tag + "完成，没有需要下载的内容");
			}
		} else {
			if (writeP) {
				System.out.println("下载标签" + tag + "完成，文件存储目录:" + destDir.toString());
			} else {
				System.out.println("下载标签" + tag + "完成，没有需要下载的内容");
			}
		}

		downloaded = true;

		if (moveExecutor != null) {
			moveExecutor.shutdown();
			try {
				moveExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
			} catch (InterruptedException e) {
				System.out.println("下载标签" + tag + "帖子失败");
				System.exit(-1);
			}
		}
	}

	/**
	 * 
	 */
	private void execute(String after) {
		TagParser tp = null;
		try {
			tp = parser.newTagParser(tag, false);
		} catch (LogicException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("初始化标签解析器失败");
			System.exit(-1);
		}
		execute(tp, after);
	}

	private void execute(TagParser tp, String after) {
		PagingResult<PagingItem> result = null;
		try {
			result = tp.paging(after, first);
		} catch (Throwable e) {
			e.printStackTrace();
			System.out.println("获取标签帖子列表失败");
			System.exit(-1);
		}
		for (PagingItem item : result.getItems()) {
			String id = item.getId();
			if (postSet.contains(id)) {
				if (increaseDownload) {
					return;
				}
				System.out.println("已经下载过帖子:" + id + "了");
				continue;
			}
			if (increasePostSet.contains(id)) {
				System.out.println("已经下载过帖子:" + id + "了");
				continue;
			}

			if (item.isSideCar() || item.isVideo()) {
				List<Url> urls = new ArrayList<>();
				boolean error = false;
				try {
					urls = parser.parsePost(item.getShortCode()).getUrls();
				} catch (LogicException e) {
					System.out.println(e.getMessage());
					error = true;
				} catch (Throwable e) {
					System.out.println("解析帖子:" + item.getShortCode() + "失败");
					error = true;
				}

				if (error) {
					logError(item.getShortCode(), item.getId());
					appendPostCode(item.getId());
					continue;
				}

				final List<Url> cUrls = urls;

				es2.execute(() -> {
					int index = 0;
					CountDownLatch cdl = new CountDownLatch(cUrls.size());
					for (Url curl : cUrls) {
						final int _index = index;
						String ext = Utils.getFileExtension(curl.getValue());
						String name = id + "_" + (++index) + "." + ext;
						Path dest = destDir.resolve(name);
						try {
							es.execute(() -> {
								try {
									DownloadF df = new DownloadF(errorF, curl.getValue(), dest, client, tempDir,
											item.getShortCode(), _index);
									df.start();
								} finally {
									cdl.countDown();
								}
							});
						} catch (Throwable e) {
							cdl.countDown();
						}
					}
					try {
						cdl.await();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					appendPostCode(item.getId());
				});

				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			} else {
				String name = id + "_1." + Utils.getFileExtension(item.getUrl());
				es.execute(() -> {
					DownloadF df = new DownloadF(errorF, item.getUrl(), destDir.resolve(name), client, tempDir,
							item.getShortCode(), 0);
					try {
						df.start();
					} finally {
						appendPostCode(item.getId());
					}
				});
			}
		}
		if (result.isHasNextPage()) {
			try {
				Thread.sleep(1000);// 防止429
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			execute(tp, result.getEndCursor());
		}
	}

	private void appendPostCode(String postCode) {
		synchronized (lock) {
			try (Writer writer = Files.newBufferedWriter(posts, StandardOpenOption.APPEND)) {
				writer.write(postCode);
				writer.write(System.lineSeparator());
			} catch (Exception e) {
			}
		}
		writeP = true;
	}

	private final class DirFilesMoveMonitor implements Runnable {

		private int id = 0;// counter
		private final Path dest;

		private void moveRest() {
			int rest = (int) quietlyList().filter(filter()).count();
			if (rest == 0) {
				return;
			}

			int v = getLast();
			if (v == -1) {
				v = 0;
			}

			Path last = dest.resolve(String.valueOf(v));
			id = v;
			if (!Files.exists(last)) {
				quietlyCreateDir(last);
			}
			int count = (int) quietlyList(last).filter(filter()).count();

			if (count < maxInDir) {
				// 先填充最后一个文件夹
				quietlyList().filter(filter()).limit(maxInDir - count).forEach(p -> {
					quietlyMove(p, last.resolve(p.getFileName().toString()));
				});

				if (rest > maxInDir - count) {
					id++;
					quietlyCreateDir(dest.resolve(String.valueOf(id)));
					moveRest();
				}
			} else {
				id++;
				quietlyCreateDir(dest.resolve(String.valueOf(id)));
				moveRest();
			}

		}

		public DirFilesMoveMonitor(Path dest) {
			super();
			this.dest = dest;
			moveRest();
		}

		@Override
		public void run() {
			while (true) {
				if (Thread.interrupted()) {
					break;
				}
				if (downloaded) {
					moveRest();
					break;
				}

				if (quietlyList().filter(filter()).count() > maxInDir) {
					moveRest();
				}

				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					System.out.println("下载终止");
					System.exit(-1);
				}

			}
		}

		private Predicate<Path> filter() {
			return p -> !p.equals(tempDir) && !p.equals(errorF) && !p.equals(errorP) && !p.equals(posts)
					&& !Files.isDirectory(p);
		}

		private Stream<Path> quietlyList() {
			return quietlyList(dest);
		}

		private void quietlyMove(Path p, Path dest) {
			try {
				Files.move(p, dest, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				System.out.println("移动文件:" + dest.resolve(p.getFileName()) + "失败");
				System.exit(-1);
			}
		}

		private void quietlyCreateDir(Path p) {
			try {
				Files.createDirectories(p);
			} catch (IOException e) {
				System.out.println("创建文件夹:" + p + "失败");
				System.exit(-1);
			}
		}

		private Stream<Path> quietlyList(Path p) {
			try {
				return Files.list(p);
			} catch (IOException e) {
				System.out.println("遍历文件夹失败");
				System.exit(-1);
			}
			return null;
		}

		private int getLast() {
			List<Path> paths = quietlyList().filter(p -> Files.isDirectory(p) && !p.equals(tempDir))
					.collect(Collectors.toList());

			int v = -1;
			for (Path p : paths) {
				try {
					int v1 = Integer.parseInt(p.getFileName().toString());
					if (v1 > v) {
						v = v1;
					}
				} catch (NumberFormatException e) {
				}
			}

			return v;
		}
	}

	private void logError(String shortCode, String id) {
		synchronized (lock) {
			try {
				String content = new String(Files.readAllBytes(errorP), StandardCharsets.UTF_8);
				ExpressionExecutors executors = Utils.readJsonForExecutors(content);

				JsonArray array = executors.toJsonArray();
				JsonObject obj = new JsonObject();
				obj.addProperty("shortcode", shortCode);
				obj.addProperty("id", id);
				array.add(obj);

				try (Writer writer = Files.newBufferedWriter(errorP)) {
					writer.write(Utils.gson.toJson(array));
				}
			} catch (Throwable e) {
			}
		}
	}

	private void downloadError() {
		if (!Files.isRegularFile(errorP)) {
			return;
		}
		String content;
		try {
			content = new String(Files.readAllBytes(errorP), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return;
		}
		ExpressionExecutors executors = Utils.readJsonForExecutors(content);
		if (executors.isNull()) {
			return;
		}
		System.out.println("开始下载失败帖子");
		JsonArray errors = new JsonArray();
		for (ExpressionExecutor executor : executors) {
			if (executor.isNull()) {
				continue;
			}
			String shortcode = executor.execute("shortcode").get();
			String id = executor.execute("id").get();
			List<Url> urls = new ArrayList<>();
			boolean error = false;
			try {
				urls = parser.parsePost(shortcode).getUrls();
			} catch (LogicException e) {
				System.out.println(e.getMessage());
				error = true;
			} catch (Throwable e) {
				System.out.println("解析帖子:" + shortcode + "失败");
				error = true;
			}

			if (error) {
				JsonObject obj = new JsonObject();
				obj.addProperty("shortcode", shortcode);
				obj.addProperty("id", id);
				errors.add(obj);
				continue;
			}

			final List<Url> cUrls = urls;

			int index = 0;
			boolean logP = false;
			for (Url curl : cUrls) {
				try {
					String ext = Utils.getFileExtension(curl.getValue());
					String name = id + "_" + (++index) + "." + ext;
					Path dest = destDir.resolve(name);
					DownloadF df = new DownloadF(errorF, curl.getValue(), dest, client, tempDir, shortcode, index - 1);
					df.start();
				} catch (Exception e) {
					if (!logP) {
						JsonObject obj = new JsonObject();
						obj.addProperty("shortcode", shortcode);
						obj.addProperty("id", id);
						errors.add(obj);
						logP = true;
					}
				}
			}
		}
		try (Writer writer = Files.newBufferedWriter(errorP)) {
			writer.write(Utils.gson.toJson(errors));
		} catch (Exception e) {
		}
	}

	public static void main(String[] args) {
		new DownloadT("陈钰琪", Paths.get("d:/downins2"), 500).start();
	}
}
