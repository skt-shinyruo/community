package com.nowcoder.community.user.infrastructure.api;

import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionStateView;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import com.nowcoder.community.user.application.RefreshTokenSessionApplicationService;
import com.nowcoder.community.user.application.result.RefreshTokenSessionResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * refresh token 会话托管服务（供 auth 模块通过 owner-domain API 调用）。
 */
@Service
public class RefreshTokenSessionApiAdapter implements UserRefreshTokenSessionQueryApi, UserRefreshTokenSessionActionApi {

    private final RefreshTokenSessionApplicationService applicationService;

    public RefreshTokenSessionApiAdapter(RefreshTokenSessionApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        if (!isValidTokenHash(tokenHash) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        applicationService.store(tokenHash.trim(), userId, familyId.trim(), expiresAt);
    }

    @Override
    public RefreshTokenSessionView find(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return null;
        }
        return toView(applicationService.find(tokenHash.trim()));
    }

    @Override
    public RefreshTokenSessionView consume(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return null;
        }
        return toView(applicationService.consume(tokenHash.trim()));
    }

    @Override
    public RefreshTokenSessionView beginRotation(String tokenHash, Instant pendingExpiresAt) {
        if (!isValidTokenHash(tokenHash) || pendingExpiresAt == null) {
            return null;
        }
        return toView(applicationService.beginRotation(tokenHash.trim(), pendingExpiresAt));
    }

    @Override
    public boolean finishRotation(
            String pendingTokenHash,
            String replacementTokenHash,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        if (!isValidTokenHash(pendingTokenHash)
                || !isValidTokenHash(replacementTokenHash)
                || userId == null
                || !StringUtils.hasText(familyId)
                || replacementExpiresAt == null) {
            return false;
        }
        return applicationService.finishRotation(
                pendingTokenHash.trim(),
                replacementTokenHash.trim(),
                userId,
                familyId.trim(),
                replacementExpiresAt
        );
    }

    @Override
    public boolean rollbackPendingRotation(String pendingTokenHash) {
        if (!isValidTokenHash(pendingTokenHash)) {
            return false;
        }
        return applicationService.rollbackPendingRotation(pendingTokenHash.trim());
    }

    @Override
    public void revoke(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return;
        }
        applicationService.revoke(tokenHash.trim());
    }

    @Override
    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        return applicationService.revokeFamily(familyId.trim());
    }

    @Override
    public int revokeByUserId(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return applicationService.revokeByUserId(userId);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return applicationService.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSessionView toView(RefreshTokenSessionResult result) {
        if (result == null) {
            return null;
        }
        return new RefreshTokenSessionView(
                result.tokenHash(),
                result.userId(),
                result.familyId(),
                result.expiresAt(),
                result.revokedAt(),
                RefreshTokenSessionStateView.valueOf(result.state().name()),
                result.pendingExpiresAt()
        );
    }

    private boolean isValidTokenHash(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return false;
        }
        String s = tokenHash.trim();
        if (s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
