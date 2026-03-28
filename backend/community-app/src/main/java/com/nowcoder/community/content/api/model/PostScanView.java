package com.nowcoder.community.content.api.model;

import java.time.Instant;
import java.util.List;

public record PostScanView(
        List<PostProjectionView> items,
        int nextAfterId,
        boolean hasMore
) {

    public PostScanView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record PostProjectionView(
            int postId,
            int userId,
            Integer categoryId,
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
