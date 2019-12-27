package me.qyh.downinsrun.parser;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.UncheckedIOException;

import me.qyh.downinsrun.DowninsCookieStore;
import me.qyh.downinsrun.DowninsHttpRoutePlanner;

public class Https {

	private static final ResponseHandler<String> stringHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			if (entity == null) {
				throw new IllegalStateException("没有响应内容");
			}
			String content = EntityUtils.toString(entity);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode >= 300) {
				throw new InvalidStateCodeException(statusCode, "错误的状态码:" + statusCode, content);
			}
			return content;
		}
	};

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

	public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36";

	public static String toString(CloseableHttpClient client, HttpRequestBase req) throws InvalidStateCodeException {
		return toString(client, req, 30, 0);
	}

	public static String toString(CloseableHttpClient client, String url) throws InvalidStateCodeException {
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", USER_AGENT);
		return toString(client, get, 30, 0);
	}

	private static String toString(CloseableHttpClient client, HttpRequestBase req, int sec, int times)
			throws InvalidStateCodeException {
		try {
			req.addHeader("user-agent", USER_AGENT);
			return client.execute(req, stringHandler);
		} catch (InvalidStateCodeException e) {
			if (e.getCode() == 429) {
				try {
					System.out.println("服务:" + req.getURI() + "请求失败：客户端请求太多，" + sec + "s后再次尝试");
					Thread.sleep(sec * 1000);
					return toString(client, req, sec + 30, times);
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
					throw new RuntimeException(e1);
				}
			}
			throw e;
		} catch (IOException e) {
			if (times == 3) {
				throw new UncheckedIOException(e);
			}
			return toString(client, req, sec, times + 1);
		} finally {
			req.releaseConnection();
		}

	}

	public static void download(CloseableHttpClient client, HttpRequestBase get, Path dest,
			DownloadProgressNotify notify, Path tempDir) throws Exception {
		if (Files.exists(dest)) {
			if (notify != null) {
				notify.notify(dest, 100);
			}
			System.out.println("文件" + dest + "已经存在");
			return;
		}
		Path temp = null;
		try (CloseableHttpResponse resp = client.execute(get)) {
			int statusCode = resp.getStatusLine().getStatusCode();
			if (statusCode >= 300) {
				throw new InvalidStateCodeException(statusCode, "异常状态码:" + statusCode, null);
			}
			HttpEntity entity = resp.getEntity();
			if (entity == null) {
				throw new IllegalStateException("空的响应内容");
			}

			temp = Files.createTempFile(tempDir, null, ".tmp");

			double fileSize = entity.getContentLength();
			if (notify != null) {
				double totalDataRead = 0;
				try (InputStream in = entity.getContent();
						OutputStream fos = Files.newOutputStream(temp);
						BufferedOutputStream bout = new BufferedOutputStream(fos, 1024)) {
					byte[] data = new byte[1024];
					int i;
					while ((i = in.read(data, 0, 1024)) >= 0) {
						totalDataRead = totalDataRead + i;
						bout.write(data, 0, i);
						BigDecimal bd = new BigDecimal((totalDataRead * 100) / fileSize);
						bd = bd.setScale(2, RoundingMode.HALF_DOWN);
						notify.notify(dest, bd.doubleValue());
					}
				}
				notify.notify(dest, 100D);
			} else {
				try (InputStream is = entity.getContent()) {
					Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
				}
			}

			Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);

			EntityUtils.consumeQuietly(entity);

		} finally {
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
		private String content;

		public InvalidStateCodeException(int code, String msg, String content) {
			super(msg);
			this.code = code;
			this.content = content;
		}

		public int getCode() {
			return code;
		}

		public String getContent() {
			return content;
		}

	}

	public static interface DownloadProgressNotify {
		public void notify(Path dest, double percent);
	}
}