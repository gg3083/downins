package me.qyh.downinsrun;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.qyh.downinsrun.Https.InvalidStateCodeException;
import me.qyh.downinsrun.InsParser.PostInfo;
import me.qyh.downinsrun.InsParser.Url;
import me.qyh.downinsrun.Jsons.ExpressionExecutor;
import me.qyh.downinsrun.Jsons.ExpressionExecutors;

public class DownloadF {

	private final Path errorF;
	private final String url;
	private final Path dest;
	private final CloseableHttpClient client;
	private final Path tempDir;
	private final String shortcode;

	private static final Object lock = new Object();

	public DownloadF(Path errorF, String url, Path dest, CloseableHttpClient client, Path tempDir, String shortcode) {
		super();
		this.errorF = errorF;
		this.url = url;
		this.dest = dest;
		this.client = client;
		this.tempDir = tempDir;
		this.shortcode = shortcode;
	}

	public static void logError(Path errorF, Path dest, String url, String shortcode) {
		synchronized (lock) {
			try {
				String content = new String(Files.readAllBytes(errorF));
				ExpressionExecutors executors = Jsons.readJsonForExecutors(content);

				JsonArray array = executors.toJsonArray();
				JsonObject obj = new JsonObject();
				obj.addProperty("url", url);
				obj.addProperty("location", Base64.getEncoder().encodeToString(dest.toString().getBytes()));
				obj.addProperty("shortcode", shortcode);
				array.add(obj);

				try (Writer writer = Files.newBufferedWriter(errorF)) {
					writer.write(Jsons.gson.toJson(array));
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
			content = new String(Files.readAllBytes(errorF));
		} catch (IOException e) {
			return;
		}
		ExpressionExecutors executors = Jsons.readJsonForExecutors(content);
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
				Path dest = Paths.get(new String(Base64.getDecoder().decode(executor.execute("location").get())));
				System.out.println(dest);
				String shortcode = executor.execute("shortcode").orElse("");
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
								int index = Integer
										.parseInt(Jsons.substringBetween(dest.getFileName().toString(), "_", "."));
								Url now = pi.getUrls().get(index);
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
					obj.addProperty("location", Base64.getEncoder().encodeToString(dest.toString().getBytes()));
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

		try (Writer writer = Files.newBufferedWriter(errorF)) {
			writer.write(Jsons.gson.toJson(errors));
		} catch (Exception e) {
		}
	}

	public boolean start() {
		System.out.println("开始下载:" + url);
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", Https.USER_AGENT);
		try {
			Https.download(client, get, dest, null, tempDir);
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
			logError(errorF, dest, url, shortcode);
			return false;
		}
	}

}
