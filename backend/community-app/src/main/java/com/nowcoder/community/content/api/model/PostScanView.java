package com.nowcoder.community.content.api.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostScanView(
        List<PostProjectionView> items,
        UUID nextAfterId,
        boolean hasMore
) {

    public PostScanView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PostProjectionView(
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

        public PostProjectionView {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
