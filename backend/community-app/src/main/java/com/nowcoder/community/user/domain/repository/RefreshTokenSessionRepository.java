package com.nowcoder.community.user.domain.repository;

import com.nowcoder.community.user.domain.model.RefreshTokenSession;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenSessionRepository {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSession find(String tokenHash);

    RefreshTokenSession consumeActive(String tokenHash);

    default RefreshTokenSession beginRotation(String tokenHash, Instant pendingExpiresAt) {
        return null;
    }

    default boolean finishRotation(
            String pendingTokenHash,
            String replacementTokenHash,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        return false;
    }

    default boolean rollbackPendingRotation(String pendingTokenHash) {
        return false;
    }

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int revokeByUserId(UUID userId);

    int deleteExpiredBefore(Instant cutoff);
}
