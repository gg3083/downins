package me.qyh.downinsrun;

import java.io.File;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import me.qyh.downinsrun.Https.InvalidStateCodeException;
import me.qyh.downinsrun.Jsons.ExpressionExecutor;
import me.qyh.downinsrun.Jsons.ExpressionExecutors;

public class InsParser {

	private static final String URL_PREFIX = "https://www.instagram.com/p/";

	private static final String USER_AGENT = Https.USER_AGENT;
	private static final String USER_URL_PREFIX = "https://www.instagram.com";

	public static final String GRAPH_IMAGE = "GraphImage";
	public static final String GRAPH_VIDEO = "GraphVideo";
	public static final String GRAPH_SIDECAR = "GraphSidecar";

	private final boolean quiet;

	private final CloseableHttpClient client;

	public InsParser(boolean quiet, CloseableHttpClient client) {
		super();
		this.quiet = quiet;
		this.client = client;
	}

	public static String getFileExtension(String path) {
		String name = new File(path).getName();
		String ext = name;
		int index = name.lastIndexOf('?');
		if (index > -1) {
			ext = ext.substring(0, index);
		}
		index = ext.lastIndexOf('.');
		if (index > -1) {
			ext = ext.substring(index + 1);
		}
		index = ext.indexOf('?');
		if (index > -1) {
			ext = ext.substring(0, index);
		}
		return ext;
	}

	public PostInfo parsePost(String p) throws Exception {
		String url = URL_PREFIX + FileUtils.cleanPath(p) + "/";
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", USER_AGENT);
		if (!quiet) {
			System.out.println("开始连接地址:" + url);
		}

		String str;
		try {
			str = Https.toString(client, get);
		} catch (InvalidStateCodeException e) {
			int code = e.getCode();
			if (code == 404) {
				throw new LogicException("帖子" + p + "不存在");
			}
			if (code == 429 && !quiet) {
				System.out.println("达到客户端连接频率限制，请稍后尝试");
			}
			throw e;
		} catch (Exception e) {
			throw e;
		}

		Document doc = Jsoup.parse(str);
		if (!quiet) {
			System.out.println("获取地址:" + url + "内容成功");
		}
		Optional<String> opsd = getSharedData(doc);
		if (!opsd.isPresent()) {
			throw new LogicException("解析帖子失败");
		}
		List<Url> urls = new ArrayList<>();
		String json = opsd.get();
		ExpressionExecutor graphql = Jsons.readJson(json)
				.executeForExecutor("entry_data->PostPage[0]->graphql->shortcode_media");

		String typename = graphql.execute("__typename").get();
		String id = graphql.execute("id").get();
		String shortcode = graphql.execute("shortcode").get();

		ExpressionExecutors children = graphql.executeForExecutors("edge_sidecar_to_children->edges");
		if (!children.isNull()) {
			for (ExpressionExecutor exe : children) {
				ExpressionExecutor node = exe.executeForExecutor("node");
				String displayUrl = node.execute("display_url").get();
				if (node.execute("is_video").map(Boolean::parseBoolean).get()) {
					urls.add(new VideoUrl(node.execute("video_url").get(), new Url(GRAPH_IMAGE, displayUrl)));
				} else {
					urls.add(new Url(GRAPH_IMAGE, displayUrl));
				}
			}
		} else {
			String displayUrl = graphql.execute("display_url").get();
			if (graphql.execute("is_video").map(Boolean::parseBoolean).get()) {
				urls.add(new VideoUrl(graphql.execute("video_url").get(), new Url(GRAPH_IMAGE, displayUrl)));
			} else {
				urls.add(new Url(GRAPH_IMAGE, displayUrl));
			}
		}
		urls.removeIf(_url -> _url.value == null || _url.value.trim().isEmpty());
		PostInfo pi = new PostInfo(typename, shortcode, id);
		for (Url _url : urls) {
			pi.addUrl(_url);
		}
		return pi;
	}

	public static Optional<String> getUsername(String url) {
		int index = url.lastIndexOf('?');
		if (index != -1) {
			url = url.substring(0, index);
		}
		index = url.lastIndexOf('/');
		if (index == -1) {
			return Optional.of(url);
		}
		if (index == url.length() - 1) {
			url = url.substring(0, index);
			int index2 = url.lastIndexOf('/');
			if (index2 == -1) {
				return Optional.of(url);
			} else {
				return Optional.of(url.substring(index2 + 1, index));
			}
		} else {
			return Optional.of(url.substring(index + 1));
		}
	}

	public static Optional<String> getShortcode(String url) {
		if (url.startsWith("https://")) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			String str = Jsons.substringBetween(url + "/", "p/", "/");
			if (str != null && !str.trim().isEmpty()) {
				return Optional.of(str);
			}
		}
		return Optional.of(url);
	}

	public static Optional<String> getTag(String url) {
		if (url.startsWith("https://")) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			String str = Jsons.substringBetween(url + "/", "tags/", "/");
			if (str != null && !str.trim().isEmpty()) {
				return Optional.of(str);
			}
		}
		return Optional.of(url);
	}

	private static String doMd5(String content) throws Exception {
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] thedigest = md.digest(content.getBytes("UTF-8"));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < thedigest.length; i++) {
			sb.append(Integer.toString((thedigest[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}

	private Optional<String> getSharedData(Document doc) {
		for (Element ele : doc.select("script")) {
			String data = ele.data().trim();
			if (data.startsWith("window._sharedData")) {
				String json = data.substring(21, data.length() - 1);
				return Optional.of(json);
			}
		}
		return Optional.empty();
	}

	public static class VideoUrl extends Url {

		private final Url display;

		public VideoUrl(String value, Url display) {
			super(GRAPH_VIDEO, value);
			this.display = display;
		}

		public Url getDisplay() {
			return display;
		}
	}

	public static class Url {
		private final String type;
		private final String value;

		public Url(String type, String value) {
			super();
			this.type = type;
			this.value = value;
		}

		public String getType() {
			return type;
		}

		public String getValue() {
			return value;
		}
	}

	public static class PostInfo {
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

	public TagParser newTagParser(String tag) throws Exception {
		return new TagParser(tag);
	}

	public UserParser newUserParser(String username) throws Exception {
		return new UserParser(username);
	}

	private String getUrlSource(String url) throws Exception {
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", USER_AGENT);
		String content;
		try {
			content = Https.toString(client, get);
		} catch (InvalidStateCodeException e) {
			int code = e.getCode();
			if (code == 429 && !quiet) {
				System.out.println("达到客户端连接频率限制，请稍后尝试");
			}
			throw e;
		} catch (Exception e) {
			throw e;
		}
		return content;
	}

	public final class UserParser {
		private static final String PAGING_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";

		private final String username;

		private String queryId;
		private String userId;
		private String rhs;

		private UserParser(String username) throws Exception {
			super();
			this.username = username;

			String url = USER_URL_PREFIX + "/" + username + "/";
			if (!quiet) {
				System.out.println("开始连接地址:" + url);
			}
			HttpGet get = new HttpGet(URLDecoder.decode(url, "utf8"));
			get.addHeader("user-agent", USER_AGENT);

			String content;
			try {
				content = Https.toString(client, get);
			} catch (InvalidStateCodeException e) {
				int code = e.getCode();
				if (code == 404) {
					throw new LogicException("用户不存在");
				}
				if (code == 429 && !quiet) {
					System.out.println("达到客户端连接频率限制，请稍后尝试");
				}
				if (!quiet) {
					System.out.println("连接地址失败");
				}
				throw e;
			} catch (Exception e) {
				if (!quiet) {
					System.out.println("连接地址失败");
				}
				throw e;
			}

			if (!quiet) {
				System.out.println("连接地址成功:" + url + "，开始设置查询参数");
			}

			Document doc = Jsoup.parse(content);
			Optional<String> sdop = getSharedData(doc);
			if (sdop.isPresent()) {
				String json = sdop.get();
				ExpressionExecutor ee = Jsons.readJson(json);
				ExpressionExecutor user = ee.executeForExecutor("entry_data->ProfilePage[0]->graphql->user");
				this.userId = user.execute("id").get();
				if (user.execute("is_private").map(Boolean::parseBoolean).get()
						&& !ee.execute("activity_counts").isPresent()) {
					throw new LogicException("访问私密账户需要设置sessionid");
				}
				this.rhs = ee.execute("config->csrf_token").get();
			} else {
				throw new LogicException("设置查询参数失败");
			}

			Elements eles = doc.select("link[type=\"text/javascript\"]");
			if (eles.isEmpty()) {
				throw new LogicException("设置查询参数失败");
			}

			String queryIdJsUrl = null;

			for (Element ele : eles) {
				String href = ele.attr("href");
				if (href.contains("rofilePageContainer.js")) {
					queryIdJsUrl = USER_URL_PREFIX + href;
					break;
				}
			}

			if (queryIdJsUrl == null) {
				throw new LogicException("设置查询参数失败");
			}

			setQueryID(queryIdJsUrl);

			if (queryId == null) {
				throw new LogicException("设置查询参数queryId失败");
			}

			if (!quiet) {
				System.out.println("设置查询参数成功");
			}
		}

		private void setQueryID(String url) throws Exception {
			String content = getUrlSource(url);
			String[] queryIds = Jsons.substringsBetween(content, "queryId:\"", "edge_owner_to_timeline_media");
			if (queryIds.length == 0) {
				return;
			}
			for (String queryId : queryIds) {
				int index = queryId.lastIndexOf("queryId");
				if (index != -1) {
					String hash = Jsons.substringBetween(queryId.substring(index), "queryId:\"", "\"");
					this.queryId = hash;
					break;
				}
			}
		}

		public UserPagingResult paging(String after, int first) throws Exception {
			URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
			String variables = String.format(PAGING_VARIABLES, this.userId, first, after);
			builder.addParameter("query_hash", this.queryId).addParameter("variables", variables);
			HttpGet get = new HttpGet(builder.build());
			String md5 = doMd5(this.rhs + ":" + variables);
			get.addHeader("x-instagram-gis", md5);
			get.addHeader("referer", URLEncoder.encode("https://www.instagram.com/" + this.username + "/", "utf8"));
			get.addHeader("user-agent", USER_AGENT);

			String content;
			try {
				content = Https.toString(client, get);
			} catch (InvalidStateCodeException e) {
				if (e.getCode() == 429 && !quiet) {
					System.out.println("达到客户端连接频率限制，请稍后尝试");
				}
				throw e;
			} catch (Exception e) {
				throw e;
			}

			ExpressionExecutor ee = Jsons.readJson(content);
			String status = ee.execute("status").orElse(null);
			if ("ok".equals(status)) {
				List<ThumbPostInfo> urls = new ArrayList<>();
				ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->user->edge_owner_to_timeline_media");
				ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
				if (ees.size() == 0) {
					return new UserPagingResult(Collections.emptyList(), "", false, 0);
				}
				int count = mediaExecutor.execute("count").map(Integer::parseInt).get();
				boolean hasNextPage = mediaExecutor.execute("page_info->has_next_page").map(Boolean::parseBoolean)
						.get();
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
					case GRAPH_SIDECAR:
						ThumbPostInfo postInfo = new ThumbPostInfo(type, shortcode, id, thumbUrl);
						ExpressionExecutors children = node.executeForExecutors("edge_sidecar_to_children->edges");
						for (ExpressionExecutor child : children) {
							ExpressionExecutor childNode = child.executeForExecutor("node");
							String childType = childNode.execute("__typename").get();
							switch (childType) {
							case GRAPH_VIDEO:
								postInfo.addUrl(new Url(GRAPH_VIDEO, childNode.execute("video_url").get()));
								break;
							case GRAPH_IMAGE:
								postInfo.addUrl(new Url(GRAPH_IMAGE, childNode.execute("display_url").get()));
								break;
							default:
								throw new RuntimeException("未知的类型:" + type);
							}
						}
						urls.add(postInfo);
						break;
					case GRAPH_VIDEO:
						ThumbPostInfo info = new ThumbPostInfo(type, shortcode, id, thumbUrl);
						info.addUrl(new Url(GRAPH_VIDEO, node.execute("video_url").get()));
						urls.add(info);
						break;
					case GRAPH_IMAGE:
						ThumbPostInfo info2 = new ThumbPostInfo(type, shortcode, id, thumbUrl);
						info2.addUrl(new Url(GRAPH_IMAGE, node.execute("display_url").get()));
						urls.add(info2);
						break;
					default:
						throw new RuntimeException("未知的类型:" + type);
					}
				}
				return new UserPagingResult(urls, endCursor, hasNextPage, count);
			}
			throw new LogicException("查询数据失败:" + content);
		}

	}

	public static class ThumbPostInfo extends PostInfo {
		private final String thumb;

		public ThumbPostInfo(String type, String shortcode, String id, String thumb) {
			super(type, shortcode, id);
			this.thumb = thumb;
		}

		public String getThumb() {
			return thumb;
		}

	}

	public static class UserPagingResult {
		private final List<ThumbPostInfo> urls;
		private final String endCursor;
		private final boolean hasNextPage;
		private final int count;

		public UserPagingResult(List<ThumbPostInfo> urls, String endCursor, boolean hasNextPage, int count) {
			super();
			this.urls = urls;
			this.endCursor = endCursor;
			this.hasNextPage = hasNextPage;
			this.count = count;
		}

		public List<ThumbPostInfo> getUrls() {
			return urls;
		}

		public String getEndCursor() {
			return endCursor;
		}

		public boolean isHasNextPage() {
			return hasNextPage;
		}

		public int getCount() {
			return count;
		}
	}

	public final class TagParser {

		private static final String URL = "https://www.instagram.com/explore/tags";
		private static final String APPID_JS_NAME = "ConsumerLibCommons.js";
		private static final String QUERYID_JS_NAME = "TagPageContainer.js";
		private static final String QUERYID_JS_NAME2 = "Consumer.js";
		private static final String TAG_VARIABLES = "{\"tag_name\":\"%s\",\"show_ranked\":false,\"first\":%s,\"after\":\"%s\"}";

		private final String tag;

		private String queryId;
		private String appId;
		private String rhx_gis;

		private List<PagingItem> tops = new ArrayList<>();

		private TagParser(String tag) throws Exception {
			super();
			this.tag = tag;

			String url = URL + "/" + tag + "/";
			if (!quiet) {
				System.out.println("开始连接地址:" + url);
			}
			HttpGet get = new HttpGet(URLDecoder.decode(url, "utf8"));
			get.addHeader("user-agent", USER_AGENT);

			String content;
			try {
				content = Https.toString(client, get);
			} catch (InvalidStateCodeException e) {
				int code = e.getCode();
				if (code == 404) {
					throw new LogicException("标签不存在");
				}
				if (code == 429 && !quiet) {
					System.out.println("达到客户端连接频率限制，请稍后尝试");
				}
				if (!quiet) {
					System.out.println("连接地址失败");
				}
				throw e;
			} catch (Exception e) {
				if (!quiet) {
					System.out.println("连接地址失败");
				}
				throw e;
			}

			if (!quiet) {
				System.out.println("连接地址成功:" + url + "，开始设置查询参数");
			}

			String[] gis = Jsons.substringsBetween(content, "csrf_token\":\"", "\"");
			if (gis.length == 0) {
				throw new LogicException("设置查询参数rhx_gis失败");
			}

			this.rhx_gis = gis[0];

			Document doc = Jsoup.parse(content);
			Elements eles = doc.select("link[type=\"text/javascript\"]");
			if (eles.isEmpty()) {
				throw new LogicException("设置查询参数失败");
			}

			String appIdJsUrl = null;
			List<String> queryIdJsUrls = new ArrayList<>();

			for (Element ele : eles) {
				String href = ele.attr("href");
				if (href.contains(APPID_JS_NAME)) {
					appIdJsUrl = USER_URL_PREFIX + href;
				}
				if (href.contains(QUERYID_JS_NAME) || href.contains(QUERYID_JS_NAME2)) {
					queryIdJsUrls.add(USER_URL_PREFIX + href);
				}
			}

			if (appIdJsUrl == null || queryIdJsUrls.isEmpty()) {
				throw new LogicException("设置查询参数失败");
			}

			setAppId(appIdJsUrl);
			setQueryID(queryIdJsUrls);

			if (!quiet) {
				System.out.println("设置查询参数成功");
			}

			Optional<String> op = getSharedData(doc);
			if (op.isPresent()) {
				List<PagingItem> items = new ArrayList<>();
				ExpressionExecutor ee = Jsons.readJson(op.get());
				for (ExpressionExecutor _ee : ee.executeForExecutors(
						"entry_data->TagPage[0]->graphql->hashtag->edge_hashtag_to_top_posts->edges")) {
					ExpressionExecutor node = _ee.executeForExecutor("node");
					PagingItem item = new PagingItem(node.execute("id").get(), node.execute("shortcode").get(),
							Boolean.parseBoolean(node.execute("is_video").get()),
							GRAPH_SIDECAR.equals(node.execute("__typename").get()),
							node.execute("thumbnail_resources[1]->src").get());
					node.execute("display_url").ifPresent(_url -> item.setUrl(_url));
					items.add(item);
				}
				this.tops = items;
			}
		}

		public List<PagingItem> tops() {
			return tops;
		}

		private void setQueryID(List<String> jsUrls) throws Exception {
			List<String> queryIds = new ArrayList<>();
			for (String jsUrl : jsUrls) {
				String content = getUrlSource(jsUrl);
				queryIds.addAll(Arrays.asList(Jsons.substringsBetween(content, "queryId:\"", "\"")));
			}
			Collections.reverse(queryIds);
			for (String queryId : queryIds) {
				if (!quiet) {
					System.out.println("开始尝试query_hash:" + queryId);
				}
				this.queryId = queryId;
				try {
					this.toExpressionExecutor("", 12);
					if (!quiet) {
						System.out.println("query_hash:" + queryId + "设置成功");
					}
				} catch (Exception e) {
					if (!quiet) {
						System.out.println("query_hash:" + queryId + "失败");
					}
				}
				Thread.sleep(1000);
				return;
			}
			throw new LogicException("获取查询参数query_hash失败");
		}

		private void setAppId(String jsUrl) throws Exception {
			String content = getUrlSource(jsUrl);
			String[] appIds = Jsons.substringsBetween(content, "instagramWebFBAppId='", "'");
			if (appIds.length == 0) {
				throw new LogicException("获取查询参数x-ig-app-id失败");
			}
			this.appId = appIds[0];
		}

		public void refreshTops() throws Exception {
			ExpressionExecutor ee = toExpressionExecutor("", 12);
			String status = ee.execute("status").orElse(null);
			if ("ok".equals(status)) {
				ExpressionExecutors ees = ee.executeForExecutors("data->hashtag->edge_hashtag_to_top_posts->edges");
				if (ees.size() == 0) {
					this.tops = new ArrayList<>();
					return;
				}
				List<PagingItem> items = new ArrayList<>();
				for (ExpressionExecutor _ee : ees) {
					ExpressionExecutor node = _ee.executeForExecutor("node");
					PagingItem item = new PagingItem(node.execute("id").get(), node.execute("shortcode").get(),
							Boolean.parseBoolean(node.execute("is_video").get()),
							GRAPH_SIDECAR.equals(node.execute("__typename").get()),
							node.execute("thumbnail_resources[1]->src").get());
					node.execute("display_url").ifPresent(url -> item.setUrl(url));
					items.add(item);
				}
				this.tops = items;
				return;
			}
			throw new LogicException("查询数据失败:" + ee);
		}

		private ExpressionExecutor toExpressionExecutor(String after, int first) throws Exception {
			URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
			String variables = String.format(TAG_VARIABLES, tag, first, after);
			builder.addParameter("query_hash", this.queryId).addParameter("variables", variables);
			HttpGet get = new HttpGet(builder.build());
			String md5 = doMd5(this.rhx_gis + ":" + variables);
			get.addHeader("x-instagram-gis", md5);
			get.addHeader("x-ig-app-id", this.appId);
			get.addHeader("referer", URLEncoder.encode(URL + "/" + tag + "/", "utf8"));
			get.addHeader("user-agent", USER_AGENT);
			get.addHeader("x-requested-with", "XMLHttpRequest");

			String content;
			try {
				content = Https.toString(client, get);
			} catch (InvalidStateCodeException e) {
				if (e.getCode() == 429 && !quiet) {
					System.out.println("达到客户端连接频率限制，请稍后尝试");
				}
				throw e;
			} catch (Exception e) {
				throw e;
			}

			return Jsons.readJson(content);
		}

		public TagPagingResult paging(String after, int first) throws Exception {
			ExpressionExecutor ee = toExpressionExecutor(after, first);
			String status = ee.execute("status").orElse(null);
			if ("ok".equals(status)) {
				ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->hashtag->edge_hashtag_to_media");
				ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
				if (ees.size() == 0) {
					return new TagPagingResult(Collections.emptyList(), "", false, 0);
				}
				int count = mediaExecutor.execute("count").map(Integer::parseInt).get();
				boolean hasNextPage = mediaExecutor.execute("page_info->has_next_page").map(Boolean::parseBoolean)
						.get();
				String endCursor = null;
				if (hasNextPage) {
					endCursor = mediaExecutor.execute("page_info->end_cursor").get();
				}
				List<PagingItem> items = new ArrayList<>();
				for (ExpressionExecutor _ee : ees) {
					ExpressionExecutor node = _ee.executeForExecutor("node");
					PagingItem item = new PagingItem(node.execute("id").get(), node.execute("shortcode").get(),
							Boolean.parseBoolean(node.execute("is_video").get()),
							GRAPH_SIDECAR.equals(node.execute("__typename").get()),
							node.execute("thumbnail_resources[1]->src").get());
					node.execute("display_url").ifPresent(url -> item.setUrl(url));
					items.add(item);
				}
				return new TagPagingResult(items, endCursor, hasNextPage, count);
			}
			throw new LogicException("查询数据失败:" + ee);
		}
	}

	public static final class PagingItem {
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

		private void setUrl(String url) {
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

	public static final class TagPagingResult {

		private final List<PagingItem> items;
		private final String endCursor;
		private final boolean hasNextPage;
		private final int count;

		public TagPagingResult(List<PagingItem> items, String endCursor, boolean hasNextPage, int count) {
			super();
			this.items = items;
			this.endCursor = endCursor;
			this.hasNextPage = hasNextPage;
			this.count = count;
		}

		public List<PagingItem> getItems() {
			return items;
		}

		public String getEndCursor() {
			return endCursor;
		}

		public boolean isHasNextPage() {
			return hasNextPage;
		}

		public int getCount() {
			return count;
		}
	}
}
