package com.nowcoder.community.ops.application.result;

import java.time.Instant;
import java.util.UUID;

public record HotCachePrewarmResult(
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
