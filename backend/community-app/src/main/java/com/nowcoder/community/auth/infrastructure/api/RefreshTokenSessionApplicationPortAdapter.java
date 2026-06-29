package com.nowcoder.community.auth.infrastructure.api;

import com.nowcoder.community.auth.application.port.RefreshTokenSessionPort;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionStateView;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RefreshTokenSessionApplicationPortAdapter implements RefreshTokenSessionPort {

    private final UserRefreshTokenSessionActionApi refreshTokenSessionActionApi;
    private final UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi;

    public RefreshTokenSessionApplicationPortAdapter(
            UserRefreshTokenSessionActionApi refreshTokenSessionActionApi,
            UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi
    ) {
        this.refreshTokenSessionActionApi = refreshTokenSessionActionApi;
        this.refreshTokenSessionQueryApi = refreshTokenSessionQueryApi;
    }

    @Override
    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        refreshTokenSessionActionApi.store(tokenHash, userId, familyId, expiresAt);
    }

    @Override
    public RefreshTokenSession find(String tokenHash) {
        return toSession(refreshTokenSessionQueryApi.find(tokenHash));
    }

    @Override
    public RefreshTokenSession consume(String tokenHash) {
        return toSession(refreshTokenSessionActionApi.consume(tokenHash));
    }

    @Override
    public RefreshTokenSession beginRotation(String tokenHash, Instant pendingExpiresAt) {
        return toSession(refreshTokenSessionActionApi.beginRotation(tokenHash, pendingExpiresAt));
    }

    @Override
    public boolean finishRotation(
            String pendingTokenHash,
            String replacementTokenHash,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        return refreshTokenSessionActionApi.finishRotation(
                pendingTokenHash,
                replacementTokenHash,
                userId,
                familyId,
                replacementExpiresAt
        );
    }

    @Override
    public boolean rollbackPendingRotation(String pendingTokenHash) {
        return refreshTokenSessionActionApi.rollbackPendingRotation(pendingTokenHash);
    }

    @Override
    public void revoke(String tokenHash) {
        refreshTokenSessionActionApi.revoke(tokenHash);
    }

    @Override
    public int revokeFamily(String familyId) {
        return refreshTokenSessionActionApi.revokeFamily(familyId);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return refreshTokenSessionActionApi.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSession toSession(RefreshTokenSessionView view) {
        if (view == null) {
            return null;
        }
        return new RefreshTokenSession(
                view.tokenHash(),
                view.userId(),
                view.familyId(),
                view.expiresAt(),
                view.revokedAt(),
                toState(view.state()),
                view.pendingExpiresAt()
        );
    }

    private RefreshTokenSessionState toState(RefreshTokenSessionStateView state) {
        if (state == null) {
            return RefreshTokenSessionState.ACTIVE;
        }
        return RefreshTokenSessionState.valueOf(state.name());
    }
}
