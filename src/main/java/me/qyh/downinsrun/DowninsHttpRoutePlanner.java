package me.qyh.downinsrun;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.protocol.HttpContext;

import me.qyh.downinsrun.parser.Configure;
import me.qyh.downinsrun.parser.DowninsConfig;

public class DowninsHttpRoutePlanner implements HttpRoutePlanner {

	private DefaultRoutePlanner drp = new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE);

	@Override
	public HttpRoute determineRoute(HttpHost target, HttpRequest request, HttpContext context) throws HttpException {
		// get proxy;
		DowninsConfig config = Configure.get().getConfig();
		if (config.getProxyAddr() != null && !config.getProxyAddr().trim().isEmpty() && config.getProxyPort() != null) {
			return new DefaultProxyRoutePlanner(new HttpHost(config.getProxyAddr().trim(), config.getProxyPort()))
					.determineRoute(target, request, context);
		}
		return drp.determineRoute(target, request, context);
	}

}
