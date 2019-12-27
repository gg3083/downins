package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.impl.client.CloseableHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;

public class QueryHashQuery {

	private CloseableHttpClient client = Https.newHttpClient();

	private static final String APP_ID = "1217981644879628";

	private static final String TAG_URL = "https://www.instagram.com/explore/tags/instagram/";
	private static final String TAG_VARIABLES = "{\"tag_name\":\"instagram\",\"first\":5,\"after\":\"\"}";
	private static final String TAG_QUERY_HASH_JS_NAME = "TagPageContainer";

	private static final String USER_URL = "https://www.instagram.com/instagram/";
	private static final String USER_VARIABLES = "{\"id\":\"25025320\",\"first\":12,\"after\":\"\"}";
	private static final String USER_QUERY_HASH_JS_NAME = "ProfilePageContainer";
	private static final String STORIES_QUERY_HASH_JS_NAME = "ProfilePageContainer";
	private static final String STORIES_VARIABLES = "{\"user_id\":\"25025320\",\"include_chaining\":true,\"include_reel\":true,\"include_suggested_users\":false,\"include_logged_out_extras\":false,\"include_highlight_reels\":true}";
	private static final String CHANNEL_QUERY_HASH_JS_NAME = "ConsumerLibCommons";
	private static final String CHANNEL_VARIABLES = "{\"id\":\"25025320\",\"first\":12,\"after\":\"\"}";
	private static final String STORY_VARIABLES = "{\"reel_ids\":[],\"tag_names\":[],\"location_ids\":[],\"highlight_reel_ids\":[%s],\"precomposed_overlay\":false,\"show_story_viewer_list\":true,\"story_viewer_fetch_count\":50,\"story_viewer_cursor\":\"\",\"stories_video_dash_manifest\":false}";
	private static final String STORY_QUERY_HASH_JS_NAME = "Consumer.js";

	private static final String URL_PREFIX = "https://www.instagram.com";

	private QueryHashQuery() {
		super();
	}

	public static QueryHashQuery INS = new QueryHashQuery();

	public static QueryHashQuery get() {
		return INS;
	}

	public QueryHash getHash() {
		QueryHash hash = new QueryHash();

		System.out.println("开始获取query_hash");
		System.out.println("开始连接地址:" + USER_URL);
		Document doc = Jsoup.parse(quietlyGetSource(USER_URL));
		System.out.println("成功获取地址内容");
		System.out.println("开始获取user query_hash");
		hash.userQueryHash = getUserQueryHash(doc);
		System.out.println("开始获取channel query_hash");
		hash.channelQueryHash = getChannelQueryHash(doc);
		System.out.println("开始获取stories query_hash");
		hash.storiesQueryHash = getStoriesQueryHash(doc);
		System.out.println("开始获取story query_hash");
		hash.storyQueryHash = getStoryQueryHash(doc, hash.storiesQueryHash);
		System.out.println("开始获取tag query_hash");
		hash.tagQueryHash = getTagQueryHash();

		return hash;
	}

	private String getTagQueryHash() {
		System.out.println("开始连接地址：" + TAG_URL);
		String content = quietlyGetSource(TAG_URL);
		System.out.println("获取地址内容成功");
		Document doc = Jsoup.parse(content);
		String token = getCsrfToken(doc);
		Elements eles = doc.select("script[src]");
		String url = null;
		for (Element ele : eles) {
			if (ele.attr("src").contains(TAG_QUERY_HASH_JS_NAME)) {
				url = ele.attr("src");
				break;
			}
		}

		if (url == null) {
			throw new RuntimeException("没有找到：" + TAG_QUERY_HASH_JS_NAME + "js文件");
		}

		System.out.println("开始连接地址：" + url);
		content = quietlyGetSource(url);
		System.out.println("获取地址内容成功");

		List<String> queryIdList = getQueryIds(content);
		System.out.println("截取到query_hash:" + Arrays.toString(queryIdList.toArray()));
		if (queryIdList.isEmpty()) {
			throw new RuntimeException("没有从：" + url + "中找到queryId");
		}
		String md5 = Utils.doMd5(token + ":" + TAG_VARIABLES);
		for (String queryId : queryIdList) {
			try {
				System.out.println("开始尝试query_hash:" + queryId);
				GraphqlQuery.create().appid(APP_ID).queryHash(queryId).addHeader("x-instagram-gis", md5)
						.variables(TAG_VARIABLES).execute(client);
				System.out.println("获取tag query_hash成功:" + queryId);
				return queryId;
			} catch (Exception e) {
				System.out.println("query_hash:" + queryId + "尝试失败，尝试下一个");
				sleep();
			}
		}

		System.out.println("获取tag query_hash失败");
		return null;
	}

	private String getUserQueryHash(Document doc) {
		String token = getCsrfToken(doc);
		Elements eles = doc.select("script[src]");

		String userHashJsUrl = null;
		for (Element ele : eles) {
			if (ele.attr("src").contains(USER_QUERY_HASH_JS_NAME)) {
				userHashJsUrl = ele.attr("src");
				break;
			}
		}

		if (userHashJsUrl == null) {
			throw new RuntimeException("没有找到：" + USER_QUERY_HASH_JS_NAME + "js文件");
		}

		System.out.println("开始连接地址：" + userHashJsUrl);
		String content = quietlyGetSource(userHashJsUrl);
		System.out.println("获取地址内容成功");

		List<String> userQueryIdList = getQueryIds(content);
		System.out.println("截取到query_hash:" + Arrays.toString(userQueryIdList.toArray()));
		if (userQueryIdList.isEmpty()) {
			throw new RuntimeException("没有从：" + userHashJsUrl + "中找到queryId");
		}

		String md5 = Utils.doMd5(token + ":" + USER_VARIABLES);
		for (String queryId : userQueryIdList) {
			try {
				System.out.println("开始尝试query_hash:" + queryId);
				ExpressionExecutor ee = GraphqlQuery.create().variables(USER_VARIABLES).appid(APP_ID).queryHash(queryId)
						.addHeader("x-instagram-gis", md5).execute(client);
				boolean valid = !ee.execute("data->user->edge_owner_to_timeline_media").isEmpty();
				if (!valid)
					continue;
				System.out.println("获取user query_hash成功:" + queryId);
				return queryId;
			} catch (Exception e) {
				System.out.println("query_hash:" + queryId + "尝试失败，尝试下一个");
				sleep();
			}
		}
		System.out.println("获取user query_hash失败");
		return null;
	}

	private String getChannelQueryHash(Document doc) {
		Elements eles = doc.select("script[src]");
		String channelHashJsUrl = null;
		for (Element ele : eles) {
			if (ele.attr("src").contains(CHANNEL_QUERY_HASH_JS_NAME)) {
				channelHashJsUrl = ele.attr("src");
				break;
			}
		}

		if (channelHashJsUrl == null) {
			throw new RuntimeException("没有找到：" + CHANNEL_QUERY_HASH_JS_NAME + "js文件");
		}

		System.out.println("开始连接地址：" + channelHashJsUrl);
		String content = quietlyGetSource(channelHashJsUrl);
		System.out.println("获取地址内容成功");

		List<String> channelQueryIdList = getQueryIds(content);
		System.out.println("截取到query_hash:" + Arrays.toString(channelQueryIdList.toArray()));

		for (String queryId : channelQueryIdList) {
			System.out.println("开始尝试query_hash:" + queryId);
			try {
				ExpressionExecutor ee = GraphqlQuery.create().variables(CHANNEL_VARIABLES).appid(APP_ID)
						.queryHash(queryId).execute(client);
				if (ee.execute("data->user->edge_felix_video_timeline").isEmpty())
					continue;
				System.out.println("获取channel query_hash成功:" + queryId);
				return queryId;
			} catch (Exception e) {
				System.out.println("query_hash:" + queryId + "尝试失败，尝试下一个");
				sleep();
			}
		}

		System.out.println("获取channel query_hash失败");
		return null;
	}

	private String getStoriesQueryHash(Document doc) {
		Elements eles = doc.select("script[src]");
		String url = null;
		for (Element ele : eles) {
			if (ele.attr("src").contains(STORIES_QUERY_HASH_JS_NAME)) {
				url = ele.attr("src");
				break;
			}
		}

		if (url == null) {
			throw new RuntimeException("没有找到：" + STORIES_QUERY_HASH_JS_NAME + "js文件");
		}

		System.out.println("开始连接地址：" + url);
		String content = quietlyGetSource(url);
		System.out.println("获取地址内容成功");

		List<String> queryIdList = getQueryIds(content);
		if (queryIdList.isEmpty()) {
			throw new RuntimeException("没有从：" + url + "中找到queryId");
		}
		System.out.println("截取到query_hash:" + Arrays.toString(queryIdList.toArray()));

		for (String queryId : queryIdList) {
			System.out.println("开始尝试query_hash:" + queryId);
			try {
				ExpressionExecutor ee = GraphqlQuery.create().appid(APP_ID).queryHash(queryId)
						.variables(STORIES_VARIABLES).execute(client);
				if (ee.execute("data->user->edge_highlight_reels").isEmpty())
					continue;
				System.out.println("获取stories query_hash成功:" + queryId);
				return queryId;
			} catch (Exception e) {
				System.out.println("query_hash:" + queryId + "尝试失败，尝试下一个");
				sleep();
			}
		}
		System.out.println("获取stories query_hash失败");
		return null;
	}

	private String getStoryQueryHash(Document doc, String storiesQueryHash) {

		ExpressionExecutor ee = GraphqlQuery.create().appid(APP_ID).variables(STORIES_VARIABLES)
				.queryHash(storiesQueryHash).execute(client);
		String first = ee.execute("data->user->edge_highlight_reels->edges[0]->node->id").get();
		String variables = String.format(STORY_VARIABLES, "\"" + first + "\"");

		Elements eles = doc.select("script[src]");
		String url = null;
		for (Element ele : eles) {
			if (ele.attr("src").contains(STORY_QUERY_HASH_JS_NAME)) {
				url = ele.attr("src");
				break;
			}
		}

		if (url == null) {
			throw new RuntimeException("没有找到：" + STORY_QUERY_HASH_JS_NAME + "js文件");
		}

		System.out.println("开始连接地址：" + url);
		String content = quietlyGetSource(url);
		System.out.println("获取地址内容成功");

		List<String> queryIdList = getQueryIds(content);
		if (queryIdList.isEmpty()) {
			throw new RuntimeException("没有从：" + url + "中找到queryId");
		}
		System.out.println("截取到query_hash:" + Arrays.toString(queryIdList.toArray()));

		for (String queryId : queryIdList) {
			System.out.println("开始尝试query_hash:" + queryId);
			try {
				ExpressionExecutor storyee = GraphqlQuery.create().appid(APP_ID).queryHash(queryId).variables(variables)
						.execute(client);
				if (storyee.execute("data->reels_media").isEmpty()) {
					continue;
				}
				System.out.println("获取story query_hash成功:" + queryId);
				return queryId;
			} catch (Exception e) {
				System.out.println("query_hash:" + queryId + "尝试失败，尝试下一个");
				sleep();
			}
		}
		System.out.println("获取story query_hash失败");
		return null;
	}

	private List<String> getQueryIds(String content) {
		List<String> queryIdList = new ArrayList<>();
		String regexString = Pattern.quote("\"") + "(.*?)" + Pattern.quote("\"");
		Pattern pattern = Pattern.compile(regexString);
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			String textInBetween = matcher.group(1);
			if (textInBetween.length() == 32) {
				String uuid = textInBetween.replaceAll("(.{8})(.{4})(.{4})(.{4})(.+)", "$1-$2-$3-$4-$5");
				try {
					UUID.fromString(uuid);
					queryIdList.add(textInBetween);
				} catch (Exception e) {
					continue;
				}
			}
		}
		return queryIdList;

	}

	private String getCsrfToken(Document doc) {
		Optional<String> sdop = InsParser.getSharedData(doc);
		if (sdop.isPresent()) {
			String json = sdop.get();
			ExpressionExecutor ee = Utils.readJson(json);
			return ee.execute("config->csrf_token").get();
		} else {
			throw new RuntimeException("获取csrf_token失败");
		}
	}

	private String quietlyGetSource(String url) {
		try {
			return Https.toString(client, url.startsWith(URL_PREFIX) ? url : URL_PREFIX + url);
		} catch (InvalidStateCodeException e) {
			throw new RuntimeException(e);
		}
	}

	private void sleep() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}

	public final class QueryHash {
		private String storyQueryHash;
		private String storiesQueryHash;
		private String tagQueryHash;
		private String userQueryHash;
		private String channelQueryHash;

		public String getStoryQueryHash() {
			return storyQueryHash;
		}

		public void setStoryQueryHash(String storyQueryHash) {
			this.storyQueryHash = storyQueryHash;
		}

		public String getStoriesQueryHash() {
			return storiesQueryHash;
		}

		public void setStoriesQueryHash(String storiesQueryHash) {
			this.storiesQueryHash = storiesQueryHash;
		}

		public String getTagQueryHash() {
			return tagQueryHash;
		}

		public void setTagQueryHash(String tagQueryHash) {
			this.tagQueryHash = tagQueryHash;
		}

		public String getUserQueryHash() {
			return userQueryHash;
		}

		public void setUserQueryHash(String userQueryHash) {
			this.userQueryHash = userQueryHash;
		}

		public String getChannelQueryHash() {
			return channelQueryHash;
		}

		public void setChannelQueryHash(String channelQueryHash) {
			this.channelQueryHash = channelQueryHash;
		}

		@Override
		public String toString() {
			return "QueryHash [storyQueryHash=" + storyQueryHash + ", storiesQueryHash=" + storiesQueryHash
					+ ", tagQueryHash=" + tagQueryHash + ", userQueryHash=" + userQueryHash + ", channelQueryHash="
					+ channelQueryHash + "]";
		}
	}
}
