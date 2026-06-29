package com.nowcoder.community.user.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenSession(
        String tokenHash,
        UUID userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenSessionState state,
        Instant pendingExpiresAt
) {
    public RefreshTokenSession(String tokenHash, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
        this(
                tokenHash,
                userId,
                familyId,
                expiresAt,
                revokedAt,
                revokedAt == null ? RefreshTokenSessionState.ACTIVE : RefreshTokenSessionState.REVOKED,
                null
        );
    }
}
