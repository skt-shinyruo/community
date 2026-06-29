package com.nowcoder.community.user.api.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenSessionView(
        String tokenHash,
        UUID userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenSessionStateView state,
        Instant pendingExpiresAt
) {
    public RefreshTokenSessionView(String tokenHash, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
        this(
                tokenHash,
                userId,
                familyId,
                expiresAt,
                revokedAt,
                revokedAt == null ? RefreshTokenSessionStateView.ACTIVE : RefreshTokenSessionStateView.REVOKED,
                null
        );
    }
}
