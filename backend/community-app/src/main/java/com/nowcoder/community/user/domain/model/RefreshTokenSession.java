package com.nowcoder.community.user.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenSession(
        String tokenHash,
        UUID userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt
) {
}
