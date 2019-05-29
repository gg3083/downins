package me.qyh.downinsrun.parser;

import java.util.List;

public final class TagPagingResult extends PagingResult<PagingItem> {

	public TagPagingResult(List<PagingItem> items, String endCursor, boolean hasNextPage, int count) {
		super(items, endCursor, hasNextPage, count);
	}

}