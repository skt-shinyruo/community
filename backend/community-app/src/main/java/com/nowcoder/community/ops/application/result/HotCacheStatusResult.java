package com.nowcoder.community.ops.application.result;

import java.time.Instant;
import java.util.UUID;

public record HotCacheStatusResult(
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
