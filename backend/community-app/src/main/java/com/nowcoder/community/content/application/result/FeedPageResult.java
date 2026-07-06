package com.nowcoder.community.content.application.result;

import java.util.List;

public record FeedPageResult(
        List<PostSummaryResult> items,
        String nextCursor,
        String rankVersion
) {
}
