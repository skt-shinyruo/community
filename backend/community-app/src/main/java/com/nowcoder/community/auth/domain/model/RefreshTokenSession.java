package com.nowcoder.community.auth.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RefreshTokenSession(
        String tokenHash,
        UUID userId,
        String familyId,
        long securityVersionAtIssue,
        Instant expiresAt,
        Instant revokedAt,
        RefreshTokenSessionState state,
        Instant pendingExpiresAt
) {
    public RefreshTokenSession {
        Objects.requireNonNull(state, "state must not be null");
    }
}
