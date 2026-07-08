package com.nowcoder.community.content.application.result;

import java.time.Instant;
import java.util.UUID;

public record HotFeedCachePrewarmResult(
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
