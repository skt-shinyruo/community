package com.nowcoder.community.ops.application.result;

import java.time.Instant;

public record HotCacheDegradationSignalResult(
        boolean degraded,
        String reason,
        Instant updatedAt
) {
}
