package com.nowcoder.community.auth.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository {

    void store(String refreshToken, UUID userId, String familyId, Instant expiresAt);

    StoredRefreshToken find(String refreshToken);

    default StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt) {
        return null;
    }

    default boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        return false;
    }

    default boolean rollbackPendingRotation(String refreshToken) {
        return false;
    }

    default StoredRefreshToken consume(String refreshToken) {
        return beginRotation(refreshToken, Instant.now().plusSeconds(30));
    }

    RevokedRefreshToken findRevoked(String refreshToken);

    void revoke(String refreshToken);

    void revokeFamily(String familyId);

    record StoredRefreshToken(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
    }

    record RevokedRefreshToken(String refreshToken, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
