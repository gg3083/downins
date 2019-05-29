package me.qyh.downinsrun.parser;

import java.util.List;

public class PagingResult<T> {
	private final List<T> items;
	private final String endCursor;
	private final boolean hasNextPage;
	private final int count;

	PagingResult(List<T> items, String endCursor, boolean hasNextPage, int count) {
		super();
		this.items = items;
		this.endCursor = endCursor;
		this.hasNextPage = hasNextPage;
		this.count = count;
	}

	public List<T> getItems() {
		return items;
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