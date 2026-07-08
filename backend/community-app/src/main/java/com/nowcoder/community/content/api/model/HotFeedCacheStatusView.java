package com.nowcoder.community.content.api.model;

import java.time.Instant;
import java.util.UUID;

public record HotFeedCacheStatusView(
        String scope,
        UUID boardId,
        String rankVersion,
        long itemCount,
        boolean summaryCacheAvailable,
        boolean degraded,
        String degradedReason,
        Instant lastPrewarmAt
) {
}
