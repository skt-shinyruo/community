package com.nowcoder.community.user.api.action;

import com.nowcoder.community.user.api.model.RefreshTokenSessionView;

import java.time.Instant;

public interface UserRefreshTokenSessionActionApi {

    void store(String tokenHash, int userId, String familyId, Instant expiresAt);

    RefreshTokenSessionView consume(String tokenHash);

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int deleteExpiredBefore(Instant cutoff);
}
