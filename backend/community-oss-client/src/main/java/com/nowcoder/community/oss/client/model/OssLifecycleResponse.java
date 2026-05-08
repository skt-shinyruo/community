package com.nowcoder.community.oss.client.model;

import java.time.Instant;
import java.util.UUID;

public record OssLifecycleResponse(
        UUID objectId,
        UUID currentVersionId,
        String status,
        boolean deletePending,
        boolean purged,
        String message,
        Instant updatedAt
) {
}
