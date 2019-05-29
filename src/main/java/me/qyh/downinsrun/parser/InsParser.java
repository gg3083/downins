package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;

public class InsParser {

	public static final String POST_URL_PREFIX = "https://www.instagram.com/p/";
	public static final String URL_PREFIX = "https://www.instagram.com";
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

	private ConcurrentHashMap<String, UserParser> upCache = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, TagParser> tpCache = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, ChannelParser> cpCache = new ConcurrentHashMap<>();

	public PostInfo parseIGTV(String tv) throws LogicException {
		return parsePost(tv);
	}

	public PostInfo parsePost(String p) throws LogicException {
		String url = POST_URL_PREFIX + Utils.cleanPath(p) + "/";
		if (!quiet) {
			System.out.println("开始连接地址:" + url);
		}
		String str;
		try {
			str = Https.toString(client, url);
		} catch (InvalidStateCodeException e) {
			if (e.getCode() == 404) {
				throw new LogicException("帖子:" + p + "不存在");
			}
			throw new RuntimeException("请求：" + url + "返回错误的状态码：" + e.getCode());
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
		ExpressionExecutor graphql = Utils.readJson(json)
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

	public static Optional<String> getIgtvShortcode(String url) {
		if (url.startsWith("https://")) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			String str = Utils.substringBetween(url + "/", "tv/", "/");
			if (str != null && !str.trim().isEmpty()) {
				return Optional.of(str);
			}
		}
		return Optional.of(url);
	}

	public static Optional<String> getChannel(String url) {
		if (url.startsWith("https://")) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			String str = Utils.substringBetween(url, "com/", "/channel");
			if (str != null && !str.trim().isEmpty()) {
				return Optional.of(str);
			}
		}
		return Optional.of(url);
	}

	public static Optional<String> getShortcode(String url) {
		if (url.startsWith("https://")) {
			while (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}
			String str = Utils.substringBetween(url + "/", "p/", "/");
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
			String str = Utils.substringBetween(url + "/", "tags/", "/");
			if (str != null && !str.trim().isEmpty()) {
				return Optional.of(str);
			}
		}
		return Optional.of(url);
	}

	public static Optional<String> getSharedData(Document doc) {
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

	public TagParser newTagParser(String tag, boolean cache) throws LogicException {
		if (cache) {
			TagParser tp;

			try {
				tp = tpCache.computeIfAbsent(tag, username -> {
					try {
						return new TagParser(quiet, client, username);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} catch (RuntimeException e) {
				Throwable cause = e.getCause();
				if (cause instanceof LogicException) {
					throw (LogicException) cause;
				} else {
					throw new RuntimeException(cause);
				}
			}

			return tp;
		}
		return new TagParser(quiet, client, tag);
	}

	public UserParser newUserParser(String username, boolean cache) throws LogicException {
		if (cache) {
			UserParser up;

			try {
				up = upCache.computeIfAbsent(username, un -> {
					try {
						return new UserParser(quiet, client, un);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} catch (RuntimeException e) {
				Throwable cause = e.getCause();
				if (cause instanceof LogicException) {
					throw (LogicException) cause;
				}
				throw e;
			}

			return up;
		}
		return new UserParser(quiet, client, username);
	}

	public ChannelParser newChannelParser(String username, boolean cache) throws LogicException {
		if (cache) {
			ChannelParser cp;

			try {
				cp = cpCache.computeIfAbsent(username, un -> {
					try {
						return new ChannelParser(quiet, client, un);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});
			} catch (RuntimeException e) {
				Throwable cause = e.getCause();
				if (cause instanceof LogicException) {
					throw (LogicException) cause;
				}
				throw e;
			}

			return cp;
		}
		return new ChannelParser(quiet, client, username);
	}

	public static String toShortcode(long id) {
		String postId = "";
		try {
			String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

			while (id > 0) {
				long remainder = (id % 64);
				id = (id - remainder) / 64;
				postId = alphabet.charAt((int) remainder) + postId;
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return postId;
	}

}
