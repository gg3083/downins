package me.qyh.downinsrun.parser;

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;
import me.qyh.downinsrun.parser.InsParser.Url;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class UserParser {

    private static final String CHANNEL_URL = "https://www.instagram.com/%s/channel/";
    private static final String CHANNEL_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";

    private static final String PAGING_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";
    private static final String STORIES_VARIABLES = "{\"user_id\":\"%s\",\"include_chaining\":true,\"include_reel\":true,\"include_suggested_users\":false,\"include_logged_out_extras\":false,\"include_highlight_reels\":true}";

    private final String username;

    private final String userId;
    private final String rhs;

    @SuppressWarnings("unused")
    private final boolean quiet;
    private final CloseableHttpClient client;

    UserParser(boolean quiet, CloseableHttpClient client, String username) throws LogicException {
        super();
        this.username = username;
        this.quiet = quiet;
        this.client = client;
        String url = InsParser.URL_PREFIX + "/" + username + "/";
        if (!quiet) {
            System.out.println("开始连接地址:" + url);
        }

        String content;
        try {
            content = Https.toString(client, url);
        } catch (InvalidStateCodeException e) {
            int code = e.getCode();
            if (code == 404) {
                throw new LogicException("用户不存在");
            }
            throw new RuntimeException("获取地址：" + url + "内容失败，响应码：" + e.getCode());
        }

        if (!quiet) {
            System.out.println("连接地址成功:" + url + "，开始设置查询参数");
        }

        Document doc = Jsoup.parse(content);
        if (doc.title().contains("Unavailable")) {
            throw new LogicException("用户不存在");
        }

        Optional<String> sdop = InsParser.getSharedData(doc);
        if (sdop.isPresent()) {
            String json = sdop.get();
            ExpressionExecutor ee = Utils.readJson(json);
            ExpressionExecutor user = ee.executeForExecutor("entry_data->ProfilePage[0]->graphql->user");
            this.userId = user.execute("id").get();
            if (user.execute("is_private").map(Boolean::parseBoolean).orElse(false)) {
                if (!user.execute("followed_by_viewer").map(Boolean::parseBoolean).orElse(false)) {
                    // 查看是否是同一用户
                    ExpressionExecutor viewer = ee.executeForExecutor("config->viewer");
                    if (viewer.isNull()) {
                        throw new LogicException("需要设置sessionid才能下载私密账户");
                    } else {
                        String id = viewer.execute("id").get();
                        if (!this.userId.equals(id)) {
                            throw new LogicException("需要关注该账户才能下载");
                        }
                    }
                }
            }
            this.rhs = ee.execute("config->csrf_token").get();
        } else {
            throw new LogicException("设置查询参数失败");
        }

        if (!quiet) {
            System.out.println("设置查询参数成功");
        }
    }

    public PagingResult<IGTVItem> channelPaging(String after, int first) {
        String variables = String.format(CHANNEL_VARIABLES, userId, first, after);
        DowninsConfig config = Configure.get().getConfig();
        ExpressionExecutor ee = GraphqlQuery.create().variables(variables)
                .addParameter("query_hash", config.getCurrentChannelQueryHash()).appid(InsParser.X_IG_APP_ID)
                .setReferer(String.format(CHANNEL_URL, username)).execute(client);
        ExpressionExecutor channelExecutor = ee.executeForExecutor("data->user->edge_felix_video_timeline");
        ExpressionExecutors ees = channelExecutor.executeForExecutors("edges");
        if (ees.size() == 0) {
            return new PagingResult<>(Collections.emptyList(), "", false, 0);
        }
        int count = channelExecutor.execute("count").map(Integer::parseInt).get();
        boolean hasNextPage = channelExecutor.execute("page_info->has_next_page").map(Boolean::parseBoolean).get();
        String endCursor = null;
        if (hasNextPage) {
            endCursor = channelExecutor.execute("page_info->end_cursor").get();
        }
        List<IGTVItem> items = new ArrayList<>();
        for (ExpressionExecutor _ee : ees) {
            ExpressionExecutor node = _ee.executeForExecutor("node");
            IGTVItem item = new IGTVItem(node.execute("shortcode").get(),
                    Double.parseDouble(node.execute("video_duration").get()), node.execute("id").get(),
                    node.execute("thumbnail_src").get());
            items.add(item);
        }
        return new PagingResult<>(items, endCursor, hasNextPage, count);
    }

    public List<Story> stories() {
        String variables = String.format(STORIES_VARIABLES, this.userId);
        DowninsConfig config = Configure.get().getConfig();
        ExpressionExecutor ee = GraphqlQuery.create().variables(variables)
                .addParameter("query_hash", config.getCurrentStoriesQueryHash())
                .setReferer("https://www.instagram.com/" + this.username + "/").execute(client);
        ExpressionExecutors edges = ee.executeForExecutors("data->user->edge_highlight_reels->edges");
        List<Story> stories = new ArrayList<>(edges.size());
        for (ExpressionExecutor edge : edges) {
            ExpressionExecutor node = edge.executeForExecutor("node");

            String thumb = node.execute("cover_media_cropped_thumbnail->url").get();
            String id = node.execute("id").get();
            stories.add(new Story(id, thumb));
        }

        return stories;
    }

    public PagingResult<ThumbPostInfo> paging(String after, int first) {
        String variables = String.format(PAGING_VARIABLES, this.userId, first, after);
        String md5 = Utils.doMd5(this.rhs + ":" + variables);
        DowninsConfig config = Configure.get().getConfig();
        ExpressionExecutor ee = GraphqlQuery.create().variables(variables)
                .addParameter("query_hash", config.getCurrentUserQueryHash()).addHeader("x-instagram-gis", md5)
                .addHeader("referer", "https://www.instagram.com/" + this.username + "/").execute(client);
        List<ThumbPostInfo> urls = new ArrayList<>();
        ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->user->edge_owner_to_timeline_media");
        ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
        if (ees.size() == 0) {
            return new PagingResult<>(Collections.emptyList(), "", false, 0);
        }
        int count = mediaExecutor.execute("count").map(Integer::parseInt).get();
        boolean hasNextPage = mediaExecutor.execute("page_info->has_next_page").map(Boolean::parseBoolean).get();
        String endCursor = null;
        if (hasNextPage) {
            endCursor = mediaExecutor.execute("page_info->end_cursor").get();
        }
        for (ExpressionExecutor _ee : ees) {
            ExpressionExecutor node = _ee.executeForExecutor("node");
            // edge_sidecar_to_children
            String thumbUrl = node.execute("thumbnail_resources[1]->src").get();
            String shortcode = node.execute("shortcode").get();
            String type = node.execute("__typename").get();
            String id = node.execute("id").get();
            switch (type) {
                case InsParser.GRAPH_SIDECAR:
                    ThumbPostInfo postInfo = new ThumbPostInfo(type, shortcode, id, thumbUrl);
                    ExpressionExecutors children = node.executeForExecutors("edge_sidecar_to_children->edges");
                    for (ExpressionExecutor child : children) {
                        ExpressionExecutor childNode = child.executeForExecutor("node");
                        String childType = childNode.execute("__typename").get();
                        switch (childType) {
                            case InsParser.GRAPH_VIDEO:
                                postInfo.addUrl(new Url(InsParser.GRAPH_VIDEO, childNode.execute("video_url").get()));
                                break;
                            case InsParser.GRAPH_IMAGE:
                                postInfo.addUrl(new Url(InsParser.GRAPH_IMAGE, childNode.execute("display_url").get()));
                                break;
                            default:
                                throw new RuntimeException("未知的类型:" + type);
                        }
                    }
                    urls.add(postInfo);
                    break;
                case InsParser.GRAPH_VIDEO:
                    ThumbPostInfo info = new ThumbPostInfo(type, shortcode, id, thumbUrl);
                    info.addUrl(new Url(InsParser.GRAPH_VIDEO, node.execute("video_url").get()));
                    urls.add(info);
                    break;
                case InsParser.GRAPH_IMAGE:
                    ThumbPostInfo info2 = new ThumbPostInfo(type, shortcode, id, thumbUrl);
                    info2.addUrl(new Url(InsParser.GRAPH_IMAGE, node.execute("display_url").get()));
                    urls.add(info2);
                    break;
                default:
                    throw new RuntimeException("未知的类型:" + type);
            }
        }
        return new PagingResult<>(urls, endCursor, hasNextPage, count);
    }
}