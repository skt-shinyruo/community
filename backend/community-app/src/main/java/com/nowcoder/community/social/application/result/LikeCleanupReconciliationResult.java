package com.nowcoder.community.social.application.result;

import java.util.UUID;

public record LikeCleanupReconciliationResult(
        UUID nextAfterEntityId,
        boolean hasMore,
        int scanned,
        int orphanTargets,
        int cleaned,
        int failed
) {
}
