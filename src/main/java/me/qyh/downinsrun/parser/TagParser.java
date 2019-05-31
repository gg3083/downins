package me.qyh.downinsrun.parser;

import java.net.URI;
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

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;

public final class TagParser {

	private static final String URL = "https://www.instagram.com/explore/tags";
	private static final String APPID_JS_NAME = "ConsumerLibCommons.js";
	private static final String QUERYID_JS_NAME = "TagPageContainer.js";
	private static final String QUERYID_JS_NAME2 = "Consumer.js";
	private static final String TAG_VARIABLES = "{\"tag_name\":\"%s\",\"show_ranked\":false,\"first\":%s,\"after\":\"%s\"}";

	private final String tag;

	private String queryId;
	private String appId = InsParser.X_IG_APP_ID;
	private String rhx_gis;

	private List<PagingItem> tops = new ArrayList<>();

	private final boolean quiet;
	private final CloseableHttpClient client;

	TagParser(boolean quiet, CloseableHttpClient client, String tag) throws LogicException {
		super();
		this.tag = tag;
		this.quiet = quiet;
		this.client = client;

		String url = URL + "/" + tag + "/";
		if (!quiet) {
			System.out.println("开始连接地址:" + url);
		}
		String content;
		try {
			content = Https.toString(client, url);
		} catch (InvalidStateCodeException e) {
			int code = e.getCode();
			if (code == 404) {
				throw new LogicException("标签:" + tag + "不存在");
			}
			throw new RuntimeException("错误的请求状态码：" + code, e);
		}

		if (!quiet) {
			System.out.println("连接地址成功:" + url + "，开始设置查询参数");
		}

		String[] gis = Utils.substringsBetween(content, "csrf_token\":\"", "\"");
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
				appIdJsUrl = InsParser.URL_PREFIX + href;
			}
			if (href.contains(QUERYID_JS_NAME) || href.contains(QUERYID_JS_NAME2)) {
				queryIdJsUrls.add(InsParser.URL_PREFIX + href);
			}
		}

		if (appIdJsUrl == null || queryIdJsUrls.isEmpty()) {
			throw new LogicException("设置查询参数失败");
		}

//		setAppId(appIdJsUrl);
		setQueryID(queryIdJsUrls);

		if (!quiet) {
			System.out.println("设置查询参数成功");
		}

		Optional<String> op = InsParser.getSharedData(doc);
		if (op.isPresent()) {
			List<PagingItem> items = new ArrayList<>();
			ExpressionExecutor ee = Utils.readJson(op.get());
			for (ExpressionExecutor _ee : ee.executeForExecutors(
					"entry_data->TagPage[0]->graphql->hashtag->edge_hashtag_to_top_posts->edges")) {
				ExpressionExecutor node = _ee.executeForExecutor("node");
				PagingItem item = new PagingItem(node.execute("id").get(), node.execute("shortcode").get(),
						Boolean.parseBoolean(node.execute("is_video").get()),
						InsParser.GRAPH_SIDECAR.equals(node.execute("__typename").get()),
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

	private void setQueryID(List<String> jsUrls) throws LogicException {
		Collections.reverse(jsUrls);
		for (String jsUrl : jsUrls) {

			String content;
			try {
				content = Https.toString(client, jsUrl);
			} catch (InvalidStateCodeException e1) {
				throw new RuntimeException("获取地址：" + jsUrl + "失败，状态码：" + e1.getCode());
			}
			List<String> queryIds = Arrays.asList(Utils.substringsBetween(content, "queryId:\"", "\""));
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
					return;
				} catch (Exception e) {
					if (!quiet) {
						System.out.println("query_hash:" + queryId + "失败");
					}
				}
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		}
		throw new LogicException("获取查询参数query_hash失败");
	}
//
//	private void setAppId(String jsUrl) throws LogicException {
//		String content;
//		try {
//			content = Https.toString(client, jsUrl);
//		} catch (InvalidStateCodeException e) {
//			throw new RuntimeException("请求:" + jsUrl + "返回错误的状态码");
//		}
//		String[] appIds = Utils.substringsBetween(content, "instagramWebFBAppId='", "'");
//		if (appIds.length == 0) {
//			throw new LogicException("获取查询参数x-ig-app-id失败");
//		}
//		this.appId = appIds[0];
//	}

	public void refreshTops() throws LogicException {
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
						InsParser.GRAPH_SIDECAR.equals(node.execute("__typename").get()),
						node.execute("thumbnail_resources[1]->src").get());
				node.execute("display_url").ifPresent(url -> item.setUrl(url));
				items.add(item);
			}
			this.tops = items;
			return;
		}
		throw new LogicException("查询数据失败:" + ee);
	}

	private ExpressionExecutor toExpressionExecutor(String after, int first) throws LogicException {
		URI uri;
		String variables = String.format(TAG_VARIABLES, tag, first, after);
		try {
			URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
			builder.addParameter("query_hash", this.queryId).addParameter("variables", variables);
			uri = builder.build();
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		HttpGet get = new HttpGet(uri);
		String md5 = Utils.doMd5(this.rhx_gis + ":" + variables);
		get.addHeader("x-instagram-gis", md5);
		get.addHeader("x-ig-app-id", this.appId);
		get.addHeader("referer", Utils.encodeUrl(URL + "/" + tag + "/"));
		get.addHeader("user-agent", Https.USER_AGENT);
		get.addHeader("x-requested-with", "XMLHttpRequest");

		String content;
		try {
			content = Https.toString(client, get);
		} catch (InvalidStateCodeException e) {
			throw new RuntimeException("获取地址：" + get.getURI() + "内容失败，响应码：" + e.getCode());
		}

		return Utils.readJson(content);
	}

	public TagPagingResult paging(String after, int first) throws LogicException {
		ExpressionExecutor ee = toExpressionExecutor(after, first);
		String status = ee.execute("status").orElse(null);
		if ("ok".equals(status)) {
			ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->hashtag->edge_hashtag_to_media");
			ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
			if (ees.size() == 0) {
				return new TagPagingResult(Collections.emptyList(), "", false, 0);
			}
			int count = mediaExecutor.execute("count").map(Integer::parseInt).get();
			boolean hasNextPage = mediaExecutor.execute("page_info->has_next_page").map(Boolean::parseBoolean).get();
			String endCursor = null;
			if (hasNextPage) {
				endCursor = mediaExecutor.execute("page_info->end_cursor").get();
			}
			List<PagingItem> items = new ArrayList<>();
			for (ExpressionExecutor _ee : ees) {
				ExpressionExecutor node = _ee.executeForExecutor("node");
				PagingItem item = new PagingItem(node.execute("id").get(), node.execute("shortcode").get(),
						Boolean.parseBoolean(node.execute("is_video").get()),
						InsParser.GRAPH_SIDECAR.equals(node.execute("__typename").get()),
						node.execute("thumbnail_resources[1]->src").get());
				node.execute("display_url").ifPresent(url -> item.setUrl(url));
				items.add(item);
			}
			return new TagPagingResult(items, endCursor, hasNextPage, count);
		}
		throw new LogicException("查询数据失败:" + ee);
	}
}
