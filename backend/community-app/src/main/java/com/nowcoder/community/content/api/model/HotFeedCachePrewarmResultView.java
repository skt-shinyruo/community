package com.nowcoder.community.content.api.model;

import java.time.Instant;
import java.util.UUID;

public record HotFeedCachePrewarmResultView(
        String scope,
        UUID boardId,
        int requestedCount,
        int loadedCount,
        int warmedCount,
        String rankVersion,
        boolean degraded,
        String degradedReason,
        Instant lastPrewarmAt
) {
}
