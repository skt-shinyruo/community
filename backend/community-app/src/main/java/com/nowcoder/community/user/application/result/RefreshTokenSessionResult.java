package com.nowcoder.community.user.application.result;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenSessionResult(
        String tokenHash,
        UUID userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt
) {
}
