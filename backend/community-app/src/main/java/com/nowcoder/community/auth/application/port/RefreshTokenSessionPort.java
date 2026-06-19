package com.nowcoder.community.auth.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenSessionPort {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSession find(String tokenHash);

    RefreshTokenSession consume(String tokenHash);

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int deleteExpiredBefore(Instant cutoff);

    record RefreshTokenSession(String tokenHash, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
