package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.Utils.ExpressionExecutors;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;

public final class TagParser {

	private static final String URL = "https://www.instagram.com/explore/tags";
	private static final String TAG_VARIABLES = "{\"tag_name\":\"%s\",\"show_ranked\":false,\"first\":%s,\"after\":\"%s\"}";

	private final String tag;

	private String rhx_gis;

	private List<PagingItem> tops = new ArrayList<>();

	@SuppressWarnings("unused")
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

	public List<PagingItem> refreshTops() {
		ExpressionExecutor ee = toExpressionExecutor("", 12);
		ExpressionExecutors ees = ee.executeForExecutors("data->hashtag->edge_hashtag_to_top_posts->edges");
		if (ees.size() == 0) {
			this.tops = new ArrayList<>();
		} else {
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
		}
		return this.tops;
	}

	private ExpressionExecutor toExpressionExecutor(String after, int first) {
		String variables = String.format(TAG_VARIABLES, tag, first, after);
		String md5 = Utils.doMd5(this.rhx_gis + ":" + variables);
		return GraphqlQuery.create().addHeader("x-instagram-gis", md5).appid(InsParser.X_IG_APP_ID)
				.queryHash(Configure.get().getConfig().getCurrentTagQueryHash()).variables(variables)
				.setReferer(URL + "/" + tag + "/").execute(client);
	}

	public PagingResult<PagingItem> paging(String after, int first) {
		ExpressionExecutor ee = toExpressionExecutor(after, first);
		ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->hashtag->edge_hashtag_to_media");
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
		return new PagingResult<>(items, endCursor, hasNextPage, count);
	}
}
