package me.qyh.downinsrun;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import me.qyh.downinsrun.Https.InvalidStateCodeException;
import me.qyh.downinsrun.Jsons.ExpressionExecutor;
import me.qyh.downinsrun.Jsons.ExpressionExecutors;

public class InsParser {

	private static final String URL_PREFIX = "https://www.instagram.com/p/";
	private static final String USER_URL_PREFIX = "https://www.instagram.com";
	private static final String PAGING_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";
	private static final String USER_AGENT = Https.USER_AGENT;

	public static final String GRAPH_IMAGE = "GraphImage";
	public static final String GRAPH_VIDEO = "GraphVideo";
	public static final String GRAPH_SIDECAR = "GraphSidecar";

	private final boolean quiet;
	private String userPageQueryId;
	private String queryIdJsUrl;

	private Map<String, QueryParam> queryParamMap = new ConcurrentHashMap<>();

	private final CloseableHttpClient client;

	public InsParser(boolean quiet, CloseableHttpClient client) {
		super();
		this.quiet = quiet;
		Properties pros = new Properties();
		try (InputStream is = InsParser.class.getResourceAsStream("parse.properties")) {
			pros.load(is);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		this.queryIdJsUrl = pros.getProperty("parse.queryIdJsUrl");
		this.userPageQueryId = pros.getProperty("parse.userPageQueryId");
		this.client = client;
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

	private String doMd5(String content) throws Exception {
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] thedigest = md.digest(content.getBytes("UTF-8"));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < thedigest.length; i++) {
			sb.append(Integer.toString((thedigest[i] & 0xff) + 0x100, 16).substring(1));
		}
		return sb.toString();
	}

	public Optional<UserPagingResult> userPaging(String username, String after) throws Exception {
		return userPaging(username, after, 12, true);
	}

	public Optional<UserPagingResult> userPaging(String username, String after, int first) throws Exception {
		return userPaging(username, after, first, true);
	}

	private Optional<UserPagingResult> userPaging(String username, String after, int first,
			boolean retryAfterRemoveParamCache) throws Exception {
		Optional<QueryParam> op = this.getQueryHash(username);
		if (!op.isPresent()) {
			return Optional.empty();
		}
		QueryParam param = op.get();
		URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
		String variables = String.format(PAGING_VARIABLES, param.userId, first, after);
		builder.addParameter("query_hash", param.queryHash).addParameter("variables", variables);
		HttpGet get = new HttpGet(builder.build());
		String md5 = doMd5(param.rhs + ":" + variables);
		get.addHeader("x-instagram-gis", md5);
		get.addHeader("referer", "https://www.instagram.com/" + username + "/");
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
			if (queryParamMap.remove(username) != null && retryAfterRemoveParamCache) {
				return userPaging(username, after, first, false);
			}
			throw e;
		}

		ExpressionExecutor ee = Jsons.readJson(content);
		String status = ee.execute("status").orElse(null);
		if ("ok".equals(status)) {
			List<PagingUrl> urls = new ArrayList<>();
			ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->user->edge_owner_to_timeline_media");
			ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
			if (ees.size() == 0) {
				UserPagingResult result = new UserPagingResult(Collections.emptyList(), "", false, 0);
				return Optional.of(result);
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
				String displayUrl = node.execute("display_url").get();
				switch (type) {
				case GRAPH_SIDECAR:
					PagingUrl pagingUrl = new PagingUrl(type, thumbUrl, shortcode, displayUrl, displayUrl, id);
					ExpressionExecutors children = node.executeForExecutors("edge_sidecar_to_children->edges");
					for (ExpressionExecutor child : children) {
						ExpressionExecutor childNode = child.executeForExecutor("node");
						String childType = childNode.execute("__typename").get();
						switch (childType) {
						case GRAPH_VIDEO:
							pagingUrl.addUrl(new Url(GRAPH_VIDEO, childNode.execute("video_url").get()));
							break;
						case GRAPH_IMAGE:
							pagingUrl.addUrl(new Url(GRAPH_IMAGE, childNode.execute("display_url").get()));
							break;
						default:
							throw new RuntimeException("未知的类型:" + type);
						}
					}
					urls.add(pagingUrl);
					break;
				case GRAPH_VIDEO:
					urls.add(new PagingUrl(type, thumbUrl, shortcode, node.execute("video_url").get(), displayUrl, id));
					break;
				case GRAPH_IMAGE:
					urls.add(new PagingUrl(type, thumbUrl, shortcode, displayUrl, displayUrl, id));
					break;
				default:
					throw new RuntimeException("未知的类型:" + type);
				}
			}
			return Optional.of(new UserPagingResult(urls, endCursor, hasNextPage, count));
		}
		return Optional.empty();
	}

	private Optional<QueryParam> getQueryHash(String username) throws Exception {
		QueryParam old = queryParamMap.get(username);
		if (old != null) {
			return Optional.of(old);
		}
		String url = USER_URL_PREFIX + "/" + username + "/";
		if (!quiet) {
			System.out.println("开始连接地址:" + url);
		}
		HttpGet get = new HttpGet(url);
		get.addHeader("user-agent", USER_AGENT);
		String jsUrl = null;
		String userId = null;
		String rhs = null;

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
			throw e;
		} catch (Exception e) {
			throw e;
		}

		if (!quiet) {
			System.out.println("获取地址:" + url + "内容成功");
		}

		Document doc = Jsoup.parse(content);
		Optional<String> sdop = getSharedData(doc);
		if (sdop.isPresent()) {
			String json = sdop.get();
			ExpressionExecutor ee = Jsons.readJson(json);
			ExpressionExecutor user = ee.executeForExecutor("entry_data->ProfilePage[0]->graphql->user");
			userId = user.execute("id").get();
			if (user.execute("is_private").map(Boolean::parseBoolean).get()
					&& !ee.execute("activity_counts").isPresent()) {
				throw new LogicException("访问私密账户需要设置sessionid");
			}
			rhs = ee.execute("rhx_gis").get();
		}

		Elements eles = doc.select("link[type=\"text/javascript\"]");
		if (eles.isEmpty()) {
			return Optional.empty();
		}
		for (Element ele : eles) {
			String href = ele.attr("href");
			if (href.contains("rofilePageContainer.js")) {
				jsUrl = USER_URL_PREFIX + href;
				break;
			}
		}
		if (jsUrl == null || userId == null || rhs == null) {
			return Optional.empty();
		}

		if (!jsUrl.equals(queryIdJsUrl)) {
			loadQueryId(jsUrl);
		}

		if (userPageQueryId == null) {
			return Optional.empty();
		}

		QueryParam param = new QueryParam(userId, userPageQueryId, rhs);
		queryParamMap.put(username, param);
		return Optional.of(param);
	}

	private void loadQueryId(String jsUrl) throws IOException {
		String[] queryIds = new String[0];
		HttpGet get2 = new HttpGet(jsUrl);
		get2.addHeader("user-agent", USER_AGENT);

		String content2;
		try {
			content2 = Https.toString(client, get2);
		} catch (InvalidStateCodeException e) {
			int code = e.getCode();
			if (code == 429 && !quiet) {
				System.out.println("达到客户端连接频率限制，请稍后尝试");
			}
			throw e;
		} catch (Exception e) {
			throw e;
		}

		queryIds = Jsons.substringsBetween(content2, "queryId:\"", "edge_owner_to_timeline_media");
		if (queryIds.length == 0) {
			return;
		}
		for (String queryId : queryIds) {
			int index = queryId.lastIndexOf("queryId");
			if (index != -1) {
				String hash = Jsons.substringBetween(queryId.substring(index), "queryId:\"", "\"");
				this.userPageQueryId = hash;
				this.queryIdJsUrl = jsUrl;
				break;
			}
		}
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

	public List<Url> parsePost(String p) throws Exception {
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
			return Collections.emptyList();
		}
		List<Url> urls = new ArrayList<>();
		String json = opsd.get();
		ExpressionExecutor graphql = Jsons.readJson(json)
				.executeForExecutor("entry_data->PostPage[0]->graphql->shortcode_media");

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
		return urls;
	}

	public static Optional<String> parse(String url) {
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

	public final class DownloadItem {
		private InputStream is;
		private String name;

		public DownloadItem(InputStream is, String name) {
			super();
			this.is = is;
			this.name = name;
		}

		public InputStream getIs() {
			return is;
		}

		public String getName() {
			return name;
		}
	}

	public class TagPagingResult extends UserPagingResult {

		public TagPagingResult(List<PagingUrl> urls, String endCursor, boolean hasNextPage, int count) {
			super(urls, endCursor, hasNextPage, count);
		}

	}

	public class UserPagingResult {
		private final List<PagingUrl> urls;
		private final String endCursor;
		private final boolean hasNextPage;
		private final int count;

		public UserPagingResult(List<PagingUrl> urls, String endCursor, boolean hasNextPage, int count) {
			super();
			this.urls = urls;
			this.endCursor = endCursor;
			this.hasNextPage = hasNextPage;
			this.count = count;
		}

		public List<PagingUrl> getUrls() {
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

	public class VideoUrl extends Url {

		private final Url display;

		public VideoUrl(String value, Url display) {
			super(GRAPH_VIDEO, value);
			this.display = display;
		}

		public Url getDisplay() {
			return display;
		}
	}

	public class PagingUrl {
		private final String type;
		private final String thumb;
		private final String shortcode;
		private final String url;
		private final String display;
		private final String id;
		private final List<Url> urls = new ArrayList<>();

		public PagingUrl(String type, String thumb, String shortcode, String url, String display, String id) {
			super();
			this.type = type;
			this.thumb = thumb;
			this.shortcode = shortcode;
			this.url = url;
			this.display = display;
			this.id = id;
		}

		void addUrl(Url url) {
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

		public String getThumb() {
			return thumb;
		}

		public String getDisplay() {
			return display;
		}

		public String getShortcode() {
			return shortcode;
		}

		public String getUrl() {
			return url;
		}

	}

	public class Url {
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

	private final class QueryParam {
		private final String userId;
		private final String queryHash;
		private final String rhs;

		public QueryParam(String userId, String queryHash, String rhs) {
			super();
			this.userId = userId;
			this.queryHash = queryHash;
			this.rhs = rhs;
		}

		@Override
		public String toString() {
			return "QueryParam [userId=" + userId + ", queryHash=" + queryHash + "]";
		}

	}
}
