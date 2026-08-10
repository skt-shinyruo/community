package com.nowcoder.community.content.application.result;

import java.util.List;

public record FeedPageResult(
        List<PostSummaryResult> items,
        String nextCursor,
        String rankVersion
) {
    public FeedPageResult {
        items = items == null ? List.of() : items.stream().toList();
        nextCursor = nextCursor == null ? "" : nextCursor;
        rankVersion = rankVersion == null ? "" : rankVersion;
    }
}
