package com.nowcoder.community.oss.application.result;

import java.time.Instant;
import java.util.UUID;

public record ObjectLifecycleResult(
        UUID objectId,
        UUID currentVersionId,
        String status,
        boolean deletePending,
        boolean purged,
        String message,
        Instant updatedAt
) {
}
