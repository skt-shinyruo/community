package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.UUID;

public record HotFeedCacheStatusResult(
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
