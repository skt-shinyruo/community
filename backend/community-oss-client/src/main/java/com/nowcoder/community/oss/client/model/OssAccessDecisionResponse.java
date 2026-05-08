package com.nowcoder.community.oss.client.model;

import java.time.Instant;
import java.util.UUID;

public record OssAccessDecisionResponse(
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
