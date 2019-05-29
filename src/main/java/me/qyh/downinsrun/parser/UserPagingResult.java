package me.qyh.downinsrun.parser;

import java.util.List;

public class UserPagingResult extends PagingResult<ThumbPostInfo> {

	public UserPagingResult(List<ThumbPostInfo> items, String endCursor, boolean hasNextPage, int count) {
		super(items, endCursor, hasNextPage, count);
	}

}