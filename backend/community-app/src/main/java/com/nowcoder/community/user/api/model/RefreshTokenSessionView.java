package com.nowcoder.community.user.api.model;

import java.time.Instant;

public record RefreshTokenSessionView(
        String tokenHash,
        int userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt
) {
}
