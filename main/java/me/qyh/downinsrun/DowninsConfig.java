package me.qyh.downinsrun;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.UncheckedIOException;

public class DowninsConfig {
	private String location;
	private String sid;
	private int threadNum;
	private String proxyAddr;
	private Integer proxyPort;

	public String getLocation() {
		return location;
	}

	public DowninsConfig setLocation(String location) {
		this.location = location;
		return this;
	}

	public String getSid() {
		return sid;
	}

	public DowninsConfig setSid(String sid) {
		this.sid = sid;
		return this;
	}

	public int getThreadNum() {
		return threadNum;
	}

	public DowninsConfig setThreadNum(int threadNum) {
		this.threadNum = threadNum;
		return this;
	}

	public String getProxyAddr() {
		return proxyAddr;
	}

	public DowninsConfig setProxyAddr(String proxyAddr) {
		this.proxyAddr = proxyAddr;
		return this;
	}

	public Integer getProxyPort() {
		return proxyPort;
	}

	public DowninsConfig setProxyPort(Integer proxyPort) {
		this.proxyPort = proxyPort;
		return this;
	}

	public Path getDownloadDir() {
		Path dir = Paths.get(location == null ? System.getProperty("user.home") + "/downins" : location);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		return dir;
	}

	public void store() throws LogicException {
		try {
			Configure.get().store(this);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

}
