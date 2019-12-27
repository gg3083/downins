package me.qyh.downinsrun.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.UncheckedIOException;

import me.qyh.downinsrun.LogicException;

public class DowninsConfig {
	private String location;
	private String sid;
	private int threadNum;
	private String proxyAddr;
	private Integer proxyPort;

	private String storyQueryHash;
	private String channelQueryHash;
	private String userQueryHash;
	private String storiesQueryHash;
	private String tagQueryHash;

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

	public DowninsConfig setStoryQueryHash(String queryHash) {
		this.storyQueryHash = queryHash;
		return this;
	}

	public String getStoryQueryHash() {
		return this.storyQueryHash;
	}

	public String getCurrentStoryQueryHash() {
		if (storyQueryHash == null)
			throw new LogicException("storyQueryHash未设置，请先设置");
		return storyQueryHash;
	}

	public String getChannelQueryHash() {
		return channelQueryHash;
	}

	public String getCurrentChannelQueryHash() {
		if (channelQueryHash == null)
			throw new LogicException("channelQueryHash未设置，请先设置");
		return channelQueryHash;
	}

	public DowninsConfig setChannelQueryHash(String channelQueryHash) {
		this.channelQueryHash = channelQueryHash;
		return this;
	}

	public String getUserQueryHash() {
		return userQueryHash;
	}

	public String getCurrentUserQueryHash() {
		if (userQueryHash == null)
			throw new LogicException("userQueryHash未设置，请先设置");
		return userQueryHash;
	}

	public DowninsConfig setUserQueryHash(String userQueryHash) {
		this.userQueryHash = userQueryHash;
		return this;
	}

	public String getStoriesQueryHash() {
		return storiesQueryHash;
	}

	public String getCurrentStoriesQueryHash() {
		if (storiesQueryHash == null)
			throw new LogicException("storiesQueryHash未设置，请先设置");
		return storiesQueryHash;
	}

	public DowninsConfig setStoriesQueryHash(String storiesQueryHash) {
		this.storiesQueryHash = storiesQueryHash;
		return this;
	}

	public String getTagQueryHash() {
		return tagQueryHash;
	}

	public String getCurrentTagQueryHash() {
		if (tagQueryHash == null)
			throw new LogicException("tagQueryHash未设置，请先设置");
		return tagQueryHash;
	}

	public DowninsConfig setTagQueryHash(String tagQueryHash) {
		this.tagQueryHash = tagQueryHash;
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

	public void store() {
		try {
			Configure.get().store(this);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

}
