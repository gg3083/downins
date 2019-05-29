package me.qyh.downinsrun;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.jsoup.UncheckedIOException;

public class Configure {

	private static final Path path = Paths.get(System.getProperty("user.home")).resolve("downins")
			.resolve("config.properties");

	private static final String SESSIONID_KEY = "downins.sessionid";
	private static final String LOCATION_KEY = "downins.location";
	private static final String THREAD_NUM_KEY = "downins.maxThreadNums";
	private static final String PROXY_ADDR_KEY = "downins.proxyAddr";
	private static final String PROXY_PORT_KEY = "downins.proxyPort";
	private static final String TIMEOUT_KEY = "downins.timeout";

	private Properties pros = new Properties();

	static {
		try {
			Files.createDirectories(path.getParent());
			if (!Files.exists(path)) {
				Files.createFile(path);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static Configure ins = new Configure();

	public Configure() {
		super();
		try (InputStream is = Files.newInputStream(path)) {
			pros.load(is);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static Configure get() {
		return ins;
	}

	public void store(DowninsConfig config) throws LogicException, IOException {

		if (config.getProxyPort() != null && (config.getProxyPort() < 1 || config.getProxyPort() > 65535)) {
			throw new LogicException("代理端口应该在1~65535之间(包含)");
		}

		if (config.getThreadNum() < 1 || config.getThreadNum() > 10) {
			throw new LogicException("下载线程数应该在1~10之间(包含)");
		}

		try {
			config.getDownloadDir();
		} catch (Exception e) {
			throw new LogicException("创建下载目录失败");
		}

		if (config.getSid() != null && !config.getSid().isEmpty())
			pros.setProperty(SESSIONID_KEY, config.getSid());
		else
			pros.remove(SESSIONID_KEY);
		if (config.getLocation() != null && !config.getLocation().isEmpty())
			pros.setProperty(LOCATION_KEY, config.getLocation());
		else
			pros.remove(LOCATION_KEY);
		if (config.getThreadNum() > 0 && config.getThreadNum() <= 20)
			pros.setProperty(THREAD_NUM_KEY, String.valueOf(config.getThreadNum()));
		if (config.getProxyAddr() != null && !config.getProxyAddr().isEmpty())
			pros.setProperty(PROXY_ADDR_KEY, config.getProxyAddr());
		else
			pros.remove(PROXY_ADDR_KEY);
		if (config.getProxyPort() != null)
			pros.setProperty(PROXY_PORT_KEY, String.valueOf(config.getProxyPort()));
		else
			pros.remove(PROXY_PORT_KEY);
		try (OutputStream os = Files.newOutputStream(path)) {
			pros.store(os, "");
		}
		try (InputStream is = Files.newInputStream(path)) {
			pros.load(is);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public DowninsConfig getConfig() {
		DowninsConfig config = new DowninsConfig();
		config.setSid(pros.getProperty(SESSIONID_KEY));
		config.setThreadNum(Integer.valueOf(pros.getProperty(THREAD_NUM_KEY, "10")));
		config.setLocation(
				pros.getProperty(LOCATION_KEY, System.getProperty("user.home") + File.separator + "downins"));
		config.setProxyAddr(pros.getProperty(PROXY_ADDR_KEY));
		String port = pros.getProperty(PROXY_PORT_KEY);
		if (port != null) {
			config.setProxyPort(Integer.parseInt(port));
		}
		return config;
	}

	public int getTimeout() {
		String timeout = pros.getProperty(TIMEOUT_KEY);
		if (timeout != null) {
			return Integer.parseInt(timeout);
		}
		return 60 * 1000;
	}

}
