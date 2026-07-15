package com.nowcoder.community.content.application.result;

import java.util.UUID;

public record PostMediaReferenceReconciliationResult(
        UUID nextAfterAssetId,
        boolean hasMore,
        int scanned,
        int pending,
        int drifted,
        int scheduled,
        int failed
) {
}
