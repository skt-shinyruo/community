package com.nowcoder.community.user.session;

import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * refresh token 会话托管服务（供 auth 模块通过 owner-domain API 调用）。
 */
@Service
public class RefreshTokenSessionService implements UserRefreshTokenSessionQueryApi, UserRefreshTokenSessionActionApi {

    private final RefreshTokenSessionRepository repository;

    public RefreshTokenSessionService(RefreshTokenSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void store(String tokenHash, int userId, String familyId, Instant expiresAt) {
        if (!isValidTokenHash(tokenHash) || userId <= 0 || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        repository.store(tokenHash.trim(), userId, familyId.trim(), expiresAt);
    }

    @Override
    public RefreshTokenSessionView find(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return null;
        }
        RefreshTokenSessionRepository.RefreshTokenRecord record = repository.find(tokenHash.trim());
        return toView(record);
    }

    @Override
    public RefreshTokenSessionView consume(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return null;
        }
        RefreshTokenSessionRepository.RefreshTokenRecord record = repository.consumeActive(tokenHash.trim(), Instant.now());
        return toView(record);
    }

    @Override
    public void revoke(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return;
        }
        repository.revoke(tokenHash.trim());
    }

    @Override
    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        return repository.revokeFamily(familyId.trim());
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return repository.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSessionView toView(RefreshTokenSessionRepository.RefreshTokenRecord record) {
        if (record == null) {
            return null;
        }
        return new RefreshTokenSessionView(
                record.tokenHash(),
                record.userId(),
                record.familyId(),
                record.expiresAt(),
                record.revokedAt()
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
