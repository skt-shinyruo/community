package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostScanResult(
        List<PostProjectionResult> items,
        UUID nextAfterId,
        boolean hasMore
) {

    public PostScanResult {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PostProjectionResult(
            UUID postId,
            UUID userId,
            UUID categoryId,
            List<String> tags,
            String title,
            String content,
            int type,
            int status,
            Instant createTime,
            Double score
    ) {

        public PostProjectionResult {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
