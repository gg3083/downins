package me.qyh.downinsrun.parser;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;

public final class ChannelParser {
	private static final String URL = "https://www.instagram.com/%s/channel/";
	private static final String APPID_JS_NAME = "ConsumerLibCommons.js";
	private static final String CHANNEL_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";

	private String userId;
	private String appId;
	private final String username;
	private String queryId;

	@SuppressWarnings("unused")
	private final boolean quiet;
	private final CloseableHttpClient client;

	ChannelParser(boolean quiet, CloseableHttpClient client, String username) throws LogicException {
		super();
		this.quiet = quiet;
		this.client = client;
		this.username = username;

		String url = String.format(URL, username);
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
			throw new RuntimeException("错误的请求状态码：" + code, e);
		}

		if (!quiet) {
			System.out.println("连接地址成功:" + url + "，开始设置查询参数");
		}

		setUserId(content);

		Document doc = Jsoup.parse(content);
		Elements eles = doc.select("link[type=\"text/javascript\"]");
		if (eles.isEmpty()) {
			throw new LogicException("设置查询参数失败");
		}

		String appIdJsUrl = null;

		for (Element ele : eles) {
			String href = ele.attr("href");
			if (href.contains(APPID_JS_NAME)) {
				appIdJsUrl = InsParser.URL_PREFIX + href;
			}
		}

		if (appIdJsUrl == null) {
			throw new LogicException("设置查询参数失败");
		}

		setAppIdAndQueryId(appIdJsUrl);

		if (!quiet) {
			System.out.println("设置查询参数成功");
		}
	}

	public ChannelPagingResult paging(String after, int first) throws LogicException {
		URI uri;
		try {
			URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
			String variables = String.format(CHANNEL_VARIABLES, userId, first, after);
			builder.addParameter("query_hash", this.queryId).addParameter("variables", variables);
			uri = builder.build();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		HttpGet get = new HttpGet(uri);
		get.addHeader("x-ig-app-id", this.appId);
		get.addHeader("referer", Utils.encodeUrl(String.format(URL, username)));
		get.addHeader("user-agent", Https.USER_AGENT);
		get.addHeader("x-requested-with", "XMLHttpRequest");

		String content;
		try {
			content = Https.toString(client, get);
		} catch (InvalidStateCodeException e) {
			if (e.getCode() == 429) {
				throw new LogicException("达到客户端连接频率限制，请稍后尝试");
			}
			throw new RuntimeException("错误的请求状态码：" + e.getCode(), e);
		}
		ExpressionExecutor ee = Utils.readJson(content);
		String status = ee.execute("status").orElse(null);
		if ("ok".equals(status)) {
			ExpressionExecutor channelExecutor = ee.executeForExecutor("data->user->edge_felix_video_timeline");
			ExpressionExecutors ees = channelExecutor.executeForExecutors("edges");
			if (ees.size() == 0) {
				return new ChannelPagingResult(Collections.emptyList(), "", false, 0);
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
			return new ChannelPagingResult(items, endCursor, hasNextPage, count);
		}
		throw new LogicException("查询数据失败:" + ee);
	}

	private void setAppIdAndQueryId(String jsUrl) throws LogicException {
		String content;
		try {
			content = Https.toString(client, jsUrl);
		} catch (InvalidStateCodeException e) {
			throw new RuntimeException("请求:" + jsUrl + "返回错误的状态码");
		}
		String[] appIds = Utils.substringsBetween(content, "instagramWebFBAppId='", "'");
		if (appIds.length == 0) {
			throw new LogicException("获取查询参数x-ig-app-id失败");
		}
		this.appId = appIds[0];
		String[] queryIds = Utils.substringsBetween(content, "USER_FELIX_MEDIA:{id:\"", "\"");
		if (queryIds.length == 0) {
			throw new LogicException("获取查询参数query_hash失败");
		}
		this.queryId = queryIds[0];
	}

	private void setUserId(String content) throws LogicException {
		Optional<String> sdop = InsParser.getSharedData(Jsoup.parse(content));
		if (sdop.isPresent()) {
			String json = sdop.get();
			ExpressionExecutor ee = Utils.readJson(json);
			ExpressionExecutor user = ee.executeForExecutor("entry_data->ProfilePage[0]->graphql->user");
			this.userId = user.execute("id").get();
			if (user.execute("is_private").map(Boolean::parseBoolean).orElse(false)) {
				if (!user.execute("followed_by_viewer").map(Boolean::parseBoolean).orElse(false)) {
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
		} else {
			throw new LogicException("设置查询参数userid失败");
		}

	}

}