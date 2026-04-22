package com.nowcoder.community.auth.service;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenStore {

    void store(String refreshToken, UUID userId, String familyId, Instant expiresAt);

    StoredRefreshToken find(String refreshToken);

    StoredRefreshToken consume(String refreshToken);

    void revoke(String refreshToken);

    void revokeFamily(String familyId);

    record StoredRefreshToken(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
    }
}
