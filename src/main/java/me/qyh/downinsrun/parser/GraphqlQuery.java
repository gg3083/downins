package me.qyh.downinsrun.parser;

import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.Utils.ExpressionExecutor;
import me.qyh.downinsrun.parser.Https.InvalidStateCodeException;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphqlQuery {

    private static final String URL = "https://www.instagram.com/graphql/query/";

    private final Map<String, String> headers = new HashMap<>();
    private final List<NameValuePair> pairs = new ArrayList<>();

    public static GraphqlQuery create() {
        return new GraphqlQuery();
    }

    public GraphqlQuery appid(String appid) {
        this.headers.put("x-ig-app-id", appid);
        return this;
    }

    public GraphqlQuery queryHash(String hash) {
        this.pairs.add(new BasicNameValuePair("query_hash", hash));
        return this;
    }

    public GraphqlQuery variables(String variables) {
        this.pairs.add(new BasicNameValuePair("variables", variables));
        return this;
    }

    public GraphqlQuery addHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public GraphqlQuery addParameter(String name, String value) {
        this.pairs.add(new BasicNameValuePair(name, value));
        return this;
    }

    public GraphqlQuery setReferer(String url) {
        this.headers.put("referer", Utils.encodeUrl(url));
        return this;
    }

    public ExpressionExecutor execute(CloseableHttpClient client) {
        HttpGet get = buildRequest();
        String content;
        try {
            content = Https.toString(client, get);
        } catch (InvalidStateCodeException e) {
            String error = e.getContent();
            try {
                ExpressionExecutor ee = Utils.readJson(error);
                error = ee.execute("message").get();
            } catch (Exception ignored) {
            }
            throw new RuntimeException("获取地址：" + get.getURI() + "内容失败，响应码：" + e.getCode() + "内容:" + error);
        }
        ExpressionExecutor ee = Utils.readJson(content);
        String status = ee.execute("status").orElse(null);
        if ("ok".equals(status)) {
            return ee;
        }
        throw new RuntimeException("地址：" + get.getURI() + "返回错误内容：" + content);
    }

    private HttpGet buildRequest() {
        URI uri;
        try {
            URIBuilder builder = new URIBuilder(URL);
            builder.addParameters(pairs);
            uri = builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        HttpGet get = new HttpGet(uri);
        get.addHeader("x-requested-with", "XMLHttpRequest");
        for (Map.Entry<String, String> it : headers.entrySet()) {
            get.addHeader(it.getKey(), it.getValue());
        }
        return get;
    }
}
