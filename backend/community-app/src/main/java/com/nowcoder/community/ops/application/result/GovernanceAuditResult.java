package com.nowcoder.community.ops.application.result;

import java.time.Instant;
import java.util.UUID;

public record GovernanceAuditResult(
        UUID id,
        String action,
        UUID actorUserId,
        String targetType,
        String targetId,
        String scope,
        String result,
        Instant createdAt
) {
}
