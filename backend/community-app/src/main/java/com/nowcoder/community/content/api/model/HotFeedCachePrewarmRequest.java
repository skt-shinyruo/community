package com.nowcoder.community.content.api.model;

import java.util.UUID;

public record HotFeedCachePrewarmRequest(
        String scope,
        UUID boardId,
        int limit,
        String reason
) {
}
