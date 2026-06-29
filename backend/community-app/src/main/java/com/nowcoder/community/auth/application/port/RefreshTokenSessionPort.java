package com.nowcoder.community.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenSessionPort {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSession find(String tokenHash);

    RefreshTokenSession consume(String tokenHash);

    RefreshTokenSession beginRotation(String tokenHash, Instant pendingExpiresAt);

    boolean finishRotation(
            String pendingTokenHash,
            String replacementTokenHash,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    );

    boolean rollbackPendingRotation(String pendingTokenHash);

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int deleteExpiredBefore(Instant cutoff);

    record RefreshTokenSession(
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

    enum RefreshTokenSessionState {
        ACTIVE,
        PENDING_ROTATION,
        CONSUMED,
        REVOKED
    }
}
