package com.nowcoder.community.auth.service;

import java.time.Instant;

public interface RefreshTokenStore {

    void store(String refreshToken, int userId, String familyId, Instant expiresAt);

    StoredRefreshToken find(String refreshToken);

    void revoke(String refreshToken);

    void revokeFamily(String familyId);

    record StoredRefreshToken(String refreshToken, int userId, String familyId, Instant expiresAt) {
    }
}

