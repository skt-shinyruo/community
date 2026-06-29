package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.RefreshTokenSessionView;

import java.time.Instant;
import java.util.UUID;

public interface UserRefreshTokenSessionActionApi {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSessionView consume(String tokenHash);

    RefreshTokenSessionView beginRotation(String tokenHash, Instant pendingExpiresAt);

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

    int revokeByUserId(UUID userId);

    int deleteExpiredBefore(Instant cutoff);
}
