package me.qyh.downinsrun.parser;

import java.net.URI;
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
import me.qyh.downinsrun.parser.InsParser.Url;

public final class UserParser {
	private static final String PAGING_VARIABLES = "{\"id\":\"%s\",\"first\":%s,\"after\":\"%s\"}";

	private final String username;

	private String queryId;
	private String userId;
	private String rhs;

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

		Elements eles = doc.select("link[type=\"text/javascript\"]");
		if (eles.isEmpty()) {
			throw new LogicException("设置查询参数失败");
		}

		String queryIdJsUrl = null;

		for (Element ele : eles) {
			String href = ele.attr("href");
			if (href.contains("rofilePageContainer.js")) {
				queryIdJsUrl = InsParser.URL_PREFIX + href;
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

	private void setQueryID(String url) throws LogicException {
		String content;
		try {
			content = Https.toString(client, url);
		} catch (InvalidStateCodeException e) {
			throw new RuntimeException("获取地址：" + url + "内容失败，响应码：" + e.getCode());
		}
		String[] queryIds = Utils.substringsBetween(content, "queryId:\"", "edge_owner_to_timeline_media");
		if (queryIds.length == 0) {
			return;
		}
		for (String queryId : queryIds) {
			int index = queryId.lastIndexOf("queryId");
			if (index != -1) {
				String hash = Utils.substringBetween(queryId.substring(index), "queryId:\"", "\"");
				this.queryId = hash;
				break;
			}
		}
	}

	public UserPagingResult paging(String after, int first) throws LogicException {
		String variables = String.format(PAGING_VARIABLES, this.userId, first, after);
		URI uri;
		try {
			URIBuilder builder = new URIBuilder("https://www.instagram.com/graphql/query/");
			uri = builder.addParameter("query_hash", this.queryId).addParameter("variables", variables).build();
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		HttpGet get = new HttpGet(uri);
		String md5 = Utils.doMd5(this.rhs + ":" + variables);
		get.addHeader("x-instagram-gis", md5);
		get.addHeader("referer", Utils.encodeUrl("https://www.instagram.com/" + this.username + "/"));
		get.addHeader("user-agent", Https.USER_AGENT);

		String content;
		try {
			content = Https.toString(client, get);
		} catch (InvalidStateCodeException e) {
			throw new RuntimeException("获取地址：" + uri + "内容失败，响应码：" + e.getCode());
		}
		ExpressionExecutor ee = Utils.readJson(content);
		String status = ee.execute("status").orElse(null);
		if ("ok".equals(status)) {
			List<ThumbPostInfo> urls = new ArrayList<>();
			ExpressionExecutor mediaExecutor = ee.executeForExecutor("data->user->edge_owner_to_timeline_media");
			ExpressionExecutors ees = mediaExecutor.executeForExecutors("edges");
			if (ees.size() == 0) {
				return new UserPagingResult(Collections.emptyList(), "", false, 0);
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
			return new UserPagingResult(urls, endCursor, hasNextPage, count);
		}
		throw new LogicException("查询数据失败:" + content);
	}
}