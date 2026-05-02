package com.nowcoder.community.user.domain.repository;

import com.nowcoder.community.user.domain.model.RefreshTokenSession;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenSessionRepository {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSession find(String tokenHash);

    RefreshTokenSession consumeActive(String tokenHash);

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int revokeByUserId(UUID userId);

    int deleteExpiredBefore(Instant cutoff);
}
