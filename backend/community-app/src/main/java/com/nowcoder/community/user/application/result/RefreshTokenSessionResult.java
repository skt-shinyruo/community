package com.nowcoder.community.user.application.result;

import com.nowcoder.community.user.domain.model.RefreshTokenSessionState;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenSessionResult(
        String tokenHash,
        UUID userId,
        String familyId,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenSessionState state,
        Instant pendingExpiresAt
) {
    public RefreshTokenSessionResult(String tokenHash, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
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
