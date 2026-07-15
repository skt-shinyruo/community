package com.nowcoder.community.auth.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository {

    void store(
            String refreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant expiresAt
    );

    StoredRefreshToken find(String refreshToken);

    StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt);

    boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant replacementExpiresAt
    );

    boolean rollbackPendingRotation(String refreshToken);

    StoredRefreshToken consume(String refreshToken);

    RevokedRefreshToken findRevoked(String refreshToken);

    void revoke(String refreshToken);

    void revokeFamily(String familyId);

    int deleteExpiredBefore(Instant cutoff);

    record StoredRefreshToken(
            String refreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant expiresAt
    ) {
    }

    record RevokedRefreshToken(String refreshToken, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
