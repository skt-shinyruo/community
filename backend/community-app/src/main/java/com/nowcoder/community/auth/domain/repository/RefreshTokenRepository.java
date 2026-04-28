package com.nowcoder.community.auth.domain.repository;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository {

    void store(String refreshToken, UUID userId, String familyId, Instant expiresAt);

    StoredRefreshToken find(String refreshToken);

    StoredRefreshToken consume(String refreshToken);

    void revoke(String refreshToken);

    void revokeFamily(String familyId);

    record StoredRefreshToken(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
    }
}
