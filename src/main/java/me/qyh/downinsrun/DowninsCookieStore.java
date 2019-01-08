package me.qyh.downinsrun;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieIdentityComparator;
import org.apache.http.impl.cookie.BasicClientCookie;

public class DowninsCookieStore implements CookieStore, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final TreeSet<Cookie> cookies;
	private final ReadWriteLock lock;

	public DowninsCookieStore() {
		super();
		this.cookies = new TreeSet<Cookie>(new CookieIdentityComparator());
		this.lock = new ReentrantReadWriteLock();
	}

	/**
	 * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent
	 * cookies. If the given cookie has already expired it will not be added, but
	 * existing values will still be removed.
	 *
	 * @param cookie
	 *            the {@link Cookie cookie} to be added
	 *
	 * @see #addCookies(Cookie[])
	 *
	 */
	@Override
	public void addCookie(final Cookie cookie) {
		if (cookie != null) {
			lock.writeLock().lock();
			try {
				// first remove any old cookie that is equivalent
				cookies.remove(cookie);
				if (!cookie.isExpired(new Date())) {
					cookies.add(cookie);
				}
			} finally {
				lock.writeLock().unlock();
			}
		}
	}

	/**
	 * Adds an array of {@link Cookie HTTP cookies}. Cookies are added individually
	 * and in the given array order. If any of the given cookies has already expired
	 * it will not be added, but existing values will still be removed.
	 *
	 * @param cookies
	 *            the {@link Cookie cookies} to be added
	 *
	 * @see #addCookie(Cookie)
	 *
	 */
	public void addCookies(final Cookie[] cookies) {
		if (cookies != null) {
			for (final Cookie cookie : cookies) {
				this.addCookie(cookie);
			}
		}
	}

	/**
	 * Returns an immutable array of {@link Cookie cookies} that this HTTP state
	 * currently contains.
	 *
	 * @return an array of {@link Cookie cookies}.
	 */
	@Override
	public List<Cookie> getCookies() {
		lock.readLock().lock();
		try {
			List<Cookie> list = new ArrayList<Cookie>(cookies);
			Optional.ofNullable(Configure.get().getConfig().getSid()).filter(sid -> !sid.isEmpty()).ifPresent(sid -> {
				BasicClientCookie cookie = new BasicClientCookie("sessionid", sid);
				cookie.setDomain(".instagram.com");
				cookie.setPath("/");
				cookie.setSecure(true);
				cookie.setAttribute(ClientCookie.DOMAIN_ATTR, "true");
				list.add(cookie);
			});
			return list;
		} finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Removes all of {@link Cookie cookies} in this HTTP state that have expired by
	 * the specified {@link java.util.Date date}.
	 *
	 * @return true if any cookies were purged.
	 *
	 * @see Cookie#isExpired(Date)
	 */
	@Override
	public boolean clearExpired(final Date date) {
		if (date == null) {
			return false;
		}
		lock.writeLock().lock();
		try {
			boolean removed = false;
			for (final Iterator<Cookie> it = cookies.iterator(); it.hasNext();) {
				if (it.next().isExpired(date)) {
					it.remove();
					removed = true;
				}
			}
			return removed;
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Clears all cookies.
	 */
	@Override
	public void clear() {
		lock.writeLock().lock();
		try {
			cookies.clear();
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public String toString() {
		lock.readLock().lock();
		try {
			return cookies.toString();
		} finally {
			lock.readLock().unlock();
		}
	}

}
