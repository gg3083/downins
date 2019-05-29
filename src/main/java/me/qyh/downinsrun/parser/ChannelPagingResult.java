package me.qyh.downinsrun.parser;

import java.util.List;

public final class ChannelPagingResult extends PagingResult<IGTVItem> {

	public ChannelPagingResult(List<IGTVItem> items, String endCursor, boolean hasNextPage, int count) {
		super(items, endCursor, hasNextPage, count);
	}

}