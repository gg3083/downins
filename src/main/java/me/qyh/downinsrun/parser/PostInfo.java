package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.List;

import me.qyh.downinsrun.parser.InsParser.Url;

public class PostInfo {
	private final String type;
	private final String shortcode;
	private final String id;
	private List<Url> urls;

	public PostInfo(String type, String shortcode, String id) {
		super();
		this.type = type;
		this.shortcode = shortcode;
		this.id = id;
	}

	public PostInfo addUrl(Url url){
		if(this.urls == null){
			this.urls = new ArrayList<>();
		}
		this.urls.add(url);
		return this;
	}

	public void setUrls(List<Url> urls){
		this.urls = urls;
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