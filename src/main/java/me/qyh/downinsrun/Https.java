package me.qyh.downinsrun;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Https {

	public static CloseableHttpClient newHttpClient() {
		CloseableHttpClient client = HttpClients.custom()
				.setDefaultRequestConfig(RequestConfig.custom()
						.setConnectionRequestTimeout(Configure.get().getTimeout())
						.setConnectTimeout(Configure.get().getTimeout()).setSocketTimeout(Configure.get().getTimeout())
						.setCookieSpec(CookieSpecs.DEFAULT).build())
				.setDefaultCookieStore(new DowninsCookieStore()).setMaxConnPerRoute(20).setMaxConnTotal(200)
				.setRoutePlanner(new DowninsHttpRoutePlanner()).build();
		return client;
	}

	public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/68.0.3440.106 Safari/537.36";

	public static String toString(CloseableHttpClient client, HttpRequestBase req) throws IOException {
		try (CloseableHttpResponse response = client.execute(req)) {
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				if (statusCode == 429) {
					try {
						System.out.println("服务:" + req.getURI() + "请求失败：客户端请求太多，10s后再次尝试");
						Thread.sleep(10 * 1000);
						return toString(client, req);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new InvalidStateCodeException(statusCode, "错误的状态码:" + statusCode);
					}
				}
				throw new InvalidStateCodeException(statusCode, "错误的状态码:" + statusCode);
			}
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new IllegalStateException("没有响应内容");
			}
			String line;
			StringBuilder sb = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent()))) {
				while ((line = reader.readLine()) != null)
					sb.append(line);
			}
			EntityUtils.consumeQuietly(entity);
			return sb.toString();
		} finally {
			req.releaseConnection();
		}
	}

	public static void download(CloseableHttpClient client, HttpRequestBase get, Path dest,
			DownloadProgressNotify notify, Path tempDir) throws Exception {
		if (Files.exists(dest)) {
			if (notify != null) {
				notify.notify(100);
			}
			System.out.println("文件" + dest + "已经存在");
			return;
		}
		DownloadProgress progress = null;
		Path temp = null;
		try (CloseableHttpResponse resp = client.execute(get)) {
			int statusCode = resp.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				if (statusCode == 429) {
					try {
						System.out.println("下载请求失败：客户端请求太多，10s后再次尝试");
						Thread.sleep(10 * 1000);
						download(client, get, dest, notify, tempDir);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new InvalidStateCodeException(statusCode, "错误的状态码:" + statusCode);
					}
				}
				throw new InvalidStateCodeException(statusCode, "异常状态码:" + statusCode);
			}
			HttpEntity entity = resp.getEntity();
			if (entity == null) {
				throw new IllegalStateException("空的响应内容");
			}

			temp = Files.createTempFile(tempDir, null, ".tmp");

			if (notify != null)
				progress = new DownloadProgress(entity.getContentLength(), temp, notify);

			try (InputStream is = entity.getContent()) {
				Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
			}

			Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);

			EntityUtils.consumeQuietly(entity);

			if (progress != null) {
				notify.notify(100D);
			}

		} finally {
			if (progress != null)
				progress.shutdown();
			get.releaseConnection();
			if (temp != null)
				try {
					Files.deleteIfExists(temp);
				} catch (Exception e) {
				}
		}
	}

	public static final class InvalidStateCodeException extends IOException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private final int code;

		public InvalidStateCodeException(int code, String msg) {
			super(msg);
			this.code = code;
		}

		public int getCode() {
			return code;
		}

	}

	public static interface DownloadProgressNotify {
		public void notify(double percent);
	}

	public static final class DownloadProgress {
		private final ScheduledExecutorService ses;

		public DownloadProgress(long contentLength, Path p, DownloadProgressNotify notify) {
			if (contentLength > 0) {
				ses = Executors.newSingleThreadScheduledExecutor();
				ses.scheduleAtFixedRate(() -> {
					long size;
					try {
						size = Files.size(p);
					} catch (Exception e) {
						size = 0;
					}
					double pct = Math.floor(size * 100 / contentLength);
					if (size == contentLength) {
						ses.shutdownNow();
					}
					notify.notify(pct);
				}, 100, 100, TimeUnit.MILLISECONDS);
			} else {
				ses = null;
			}
		}

		public void shutdown() {
			if (ses != null && !ses.isShutdown()) {
				ses.shutdownNow();
			}

		}
	}
}