package com.nowcoder.community.content.application.result;

import java.util.List;

public record CommentPageResult(
        List<CommentResult> items,
        String nextCursor
) {
    public CommentPageResult {
        items = items == null ? List.of() : List.copyOf(items);
        nextCursor = nextCursor == null ? "" : nextCursor;
    }
}
