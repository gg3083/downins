package me.qyh.downinsrun.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PagingResult<T> {
	private final List<T> items;
	private final String endCursor;
	private final boolean hasNextPage;
	private final int count;

	public PagingResult(List<T> items, String endCursor, boolean hasNextPage, int count) {
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

	public <E> PagingResult<E> to(Function<T, E> function) {
		List<E> es = new ArrayList<>();
		for (T t : items) {
			E e = function.apply(t);
			es.add(e);
		}
		return new PagingResult<E>(es, this.endCursor, this.hasNextPage, this.count);
	}
}