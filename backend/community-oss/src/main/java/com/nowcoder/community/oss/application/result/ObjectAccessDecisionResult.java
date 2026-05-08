package com.nowcoder.community.oss.application.result;

import java.time.Instant;
import java.util.UUID;

public record ObjectAccessDecisionResult(
        UUID grantId,
        UUID objectId,
        UUID versionId,
        String principalType,
        String principalValue,
        String permission,
        Instant expiresAt,
        String createdBy,
        Instant createdAt,
        Instant revokedAt,
        boolean active
) {
}
