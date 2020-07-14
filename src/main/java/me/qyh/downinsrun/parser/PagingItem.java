package me.qyh.downinsrun.parser;

public final class PagingItem {
    private final String shortCode;
    private final boolean video;
    private final boolean sideCar;
    private String url;
    private final String id;
    private final String thumb;

    public PagingItem(String id, String shortCode, boolean video, boolean sideCar, String thumb) {
        super();
        this.id = id;
        this.shortCode = shortCode;
        this.video = video;
        this.sideCar = sideCar;
        this.thumb = thumb;
    }

    public String getThumb() {
        return thumb;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getShortCode() {
        return shortCode;
    }

    public boolean isVideo() {
        return video;
    }

    public boolean isSideCar() {
        return sideCar;
    }
}