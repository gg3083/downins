package me.qyh.downinsrun.parser;

public class ThumbPostInfo extends PostInfo {
    private final String thumb;

    public ThumbPostInfo(String type, String shortcode, String id, String thumb) {
        super(type, shortcode, id);
        this.thumb = thumb;
    }

    public String getThumb() {
        return thumb;
    }
}