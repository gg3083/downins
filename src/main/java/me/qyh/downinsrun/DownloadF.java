package me.qyh.downinsrun;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import me.qyh.downinsrun.Https.InvalidStateCodeException;
import me.qyh.downinsrun.Jsons.ExpressionExecutor;
import me.qyh.downinsrun.Jsons.ExpressionExecutors;

public class DownloadF {

	private final Path errorF;
	private final String url;
	private final Path dest;
	private final CloseableHttpClient client;
	private final Path tempDir;

	private static final Object lock = new Object();

	public DownloadF(Path errorF, String url, Path dest, CloseableHttpClient client, Path tempDir) {
		super();
		this.errorF = errorF;
		this.url = url;
		this.dest = dest;
		this.client = client;
		this.tempDir = tempDir;
	}

	public static void logError(Path errorF, Path dest, String url) {
		synchronized (lock) {
			try {
				String content = new String(Files.readAllBytes(errorF));
				ExpressionExecutors executors = Jsons.readJsonForExecutors(content);

				JsonArray array = executors.toJsonArray();
				JsonObject obj = new JsonObject();
				obj.addProperty("url", url);
				obj.addProperty("location", dest.toString());
				array.add(obj);

				try (Writer writer = Files.newBufferedWriter(errorF)) {
					writer.write(Jsons.gson.toJson(array));
				}
			} catch (Throwable e) {
			}
		}
	}

	public synchronized static void downloadError(CloseableHttpClient client, Path errorF, Path tempDir) {
		downloadError(client, errorF, tempDir, null);
	}

	public synchronized static void downloadError(CloseableHttpClient client, Path errorF, Path tempDir,
			AtomicInteger counter) {
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
				Path dest = Paths.get(executor.execute("location").get());
				HttpGet get = new HttpGet(url);
				get.addHeader("user-agent", Https.USER_AGENT);

				System.out.println("开始下载文件:" + url);
				try {
					Https.download(client, get, dest, null, tempDir);
					System.out.println(url + "下载成功，存放位置:" + dest);
					if (counter != null) {
						counter.incrementAndGet();
					}
				} catch (Throwable e) {
					if (e instanceof InvalidStateCodeException) {
						InvalidStateCodeException isce = (InvalidStateCodeException) e;
						int code = isce.getCode();
						if (code == 404) {
							System.out.println(url + "对应的文件不存在");
							return;
						}
					}
					System.out.println(url + "下载失败！！！");
					try {
						Files.deleteIfExists(dest);
					} catch (IOException e1) {
					}

					JsonObject obj = new JsonObject();
					obj.addProperty("url", url);
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

		try (Writer writer = Files.newBufferedWriter(errorF)) {
			writer.write(Jsons.gson.toJson(errors));
		} catch (Exception e) {
		}
	}

	public void start() {
		this.start(null);
	}

	public void start(AtomicInteger counter) {
		System.out.println("开始下载:" + url);
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", Https.USER_AGENT);
		try {
			Https.download(client, get, dest, null, tempDir);
			if (counter != null) {
				counter.incrementAndGet();
			}
			System.out.println(url + "下载成功，存放位置:" + dest);
		} catch (Throwable e) {
			if (e instanceof InvalidStateCodeException) {
				InvalidStateCodeException isce = (InvalidStateCodeException) e;
				int code = isce.getCode();
				if (code == 404) {
					System.out.println(url + "对应的文件不存在");
					return;
				}
			}
			System.out.println(url + "下载失败！！！");
			try {
				Files.deleteIfExists(dest);
			} catch (IOException e1) {
			}
			logError(errorF, dest, url);
		}
	}

}
