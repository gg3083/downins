package me.qyh.downinsrun.parser;

public final class IGTVItem {
	private final String shortcode;
	private double duration;
	private final String id;
	private final String thumb;

	IGTVItem(String shortcode, double duration, String id, String thumb) {
		super();
		this.shortcode = shortcode;
		this.duration = duration;
		this.id = id;
		this.thumb = thumb;
	}

	public String getShortcode() {
		return shortcode;
	}

	public double getDuration() {
		return duration;
	}

	public String getId() {
		return id;
	}

	public String getThumb() {
		return thumb;
	}

	@Override
	public String toString() {
		return "IGTVItem [shortcode=" + shortcode + ", duration=" + duration + ", id=" + id + ", thumb=" + thumb + "]";
	}

}