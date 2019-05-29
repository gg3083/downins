package me.qyh.downinsrun;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https;
import me.qyh.downinsrun.parser.Https.DownloadProgressNotify;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;
import me.qyh.downinsrun.parser.InsParser;
import me.qyh.downinsrun.parser.InsParser.Url;
import me.qyh.downinsrun.parser.PostInfo;

public class DownloadF {

	private final Path errorF;
	private final String url;
	private final Path dest;
	private final CloseableHttpClient client;
	private final Path tempDir;
	private final String shortcode;
	private final int index;

	private final DownloadProgressNotify notify;

	private static final Object lock = new Object();

	public DownloadF(Path errorF, String url, Path dest, CloseableHttpClient client, Path tempDir, String shortcode,
			int index, DownloadProgressNotify notify) {
		super();
		this.errorF = errorF;
		this.url = url;
		this.dest = dest;
		this.client = client;
		this.tempDir = tempDir;
		this.shortcode = shortcode;
		this.index = index;
		this.notify = notify;
	}

	public DownloadF(Path errorF, String url, Path dest, CloseableHttpClient client, Path tempDir, String shortcode,
			int index) {
		this(errorF, url, dest, client, tempDir, shortcode, index, null);
	}

	public static void logError(Path errorF, Path dest, String url, String shortcode, int index) {
		synchronized (lock) {
			try {
				String content = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
				ExpressionExecutors executors = Utils.readJsonForExecutors(content);

				JsonArray array = executors.toJsonArray();
				JsonObject obj = new JsonObject();
				obj.addProperty("url", url);
				obj.addProperty("location", dest.toString());
				obj.addProperty("shortcode", shortcode);
				obj.addProperty("index", index);
				array.add(obj);

				try (Writer writer = Files.newBufferedWriter(errorF, StandardCharsets.UTF_8)) {
					writer.write(Utils.gson.toJson(array));
				}
			} catch (Throwable e) {
			}
		}
	}

	public synchronized static void downloadError(InsParser parser, CloseableHttpClient client, Path errorF,
			Path tempDir) {
		if (!Files.isRegularFile(errorF)) {
			return;
		}
		String content;
		try {
			content = new String(Files.readAllBytes(errorF), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return;
		}
		ExpressionExecutors executors = Utils.readJsonForExecutors(content);
		if (executors.isNull()) {
			return;
		}
		System.out.println("开始下载失败文件");
		ExecutorService es = Executors
				.newFixedThreadPool(Math.min(executors.size(), Configure.get().getConfig().getThreadNum()));
		JsonArray errors = new JsonArray();
		for (ExpressionExecutor executor : executors) {
			if (executor.isNull()) {
				continue;
			}
			es.execute(() -> {
				String url = executor.execute("url").get();
				Path dest = Paths.get(executor.execute("location").get());
				String shortcode = executor.execute("shortcode").orElse("");
				int index = executor.execute("index").map(Integer::parseInt).get();
				HttpGet get = new HttpGet(url);
				get.addHeader("user-agent", Https.USER_AGENT);

				System.out.println("开始下载文件:" + url);
				try {
					Https.download(client, get, dest, null, tempDir);
					System.out.println(url + "下载成功，存放位置:" + dest);
				} catch (Throwable e) {
					if (e instanceof InvalidStateCodeException) {
						InvalidStateCodeException isce = (InvalidStateCodeException) e;
						int code = isce.getCode();
						if (code == 404) {
							System.out.println(url + "对应的文件不存在");
							return;
						}

						if (code == 403 && !shortcode.isEmpty()) {
							try {
								PostInfo pi = parser.parsePost(shortcode);
								Thread.sleep(1000);
								Url now = pi.getUrls().get(index - 1);
								HttpGet get2 = new HttpGet(now.getValue());
								get2.addHeader("user-agent", Https.USER_AGENT);
								Https.download(client, get2, dest, null, tempDir);
								return;
							} catch (Throwable e2) {
							}
						}

					}
					System.out.println(url + "下载失败！！！");
					try {
						Files.deleteIfExists(dest);
					} catch (IOException e1) {
					}

					JsonObject obj = new JsonObject();
					obj.addProperty("url", url);
					obj.addProperty("shortcode", shortcode);
					obj.addProperty("location", dest.toString());
					errors.add(obj);
				}
			});
		}

		es.shutdown();
		try {
			es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e1) {
			System.out.println("下载失败文件失败");
			System.exit(-1);
		}

		try (Writer writer = Files.newBufferedWriter(errorF, StandardCharsets.UTF_8)) {
			writer.write(Utils.gson.toJson(errors));
		} catch (Exception e) {
		}
	}

	public boolean start() {
		System.out.println("开始下载:" + url);
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", Https.USER_AGENT);
		try {
			Https.download(client, get, dest, notify, tempDir);
			System.out.println(url + "下载成功，存放位置:" + dest);
			return true;
		} catch (Throwable e) {
			if (e instanceof InvalidStateCodeException) {
				InvalidStateCodeException isce = (InvalidStateCodeException) e;
				int code = isce.getCode();
				if (code == 404) {
					System.out.println(url + "对应的文件不存在");
					return true;
				}
			}
			System.out.println(url + "下载失败！！！");
			try {
				Files.deleteIfExists(dest);
			} catch (IOException e1) {
			}
			logError(errorF, dest, url, shortcode, index);
			return false;
		}
	}

}
