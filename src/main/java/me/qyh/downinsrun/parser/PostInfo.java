package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.List;

import me.qyh.downinsrun.parser.InsParser.Url;

public class PostInfo {
	private final String type;
	private final String shortcode;
	private final String id;
	private final List<Url> urls = new ArrayList<>();

	public PostInfo(String type, String shortcode, String id) {
		super();
		this.type = type;
		this.shortcode = shortcode;
		this.id = id;
	}

	public void addUrl(Url url) {
		urls.add(url);
	}

	public List<Url> getUrls() {
		return urls;
	}

	public String getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public String getShortcode() {
		return shortcode;
	}

}