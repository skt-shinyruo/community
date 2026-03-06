package com.nowcoder.community.user.session;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * refresh token 会话托管服务（供 auth-service internal 调用）。
 */
@Service
public class RefreshTokenSessionService {

    private final RefreshTokenSessionRepository repository;

    public RefreshTokenSessionService(RefreshTokenSessionRepository repository) {
        this.repository = repository;
    }

    public void store(String tokenHash, int userId, String familyId, Instant expiresAt) {
        if (!isValidTokenHash(tokenHash) || userId <= 0 || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        repository.store(tokenHash.trim(), userId, familyId.trim(), expiresAt);
    }

    public RefreshTokenRecord find(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return null;
        }
        RefreshTokenSessionRepository.RefreshTokenRecord r = repository.find(tokenHash.trim());
        if (r == null) {
            return null;
        }
        return new RefreshTokenRecord(r.tokenHash(), r.userId(), r.familyId(), r.expiresAt(), r.revokedAt());
    }

    public void revoke(String tokenHash) {
        if (!isValidTokenHash(tokenHash)) {
            return;
        }
        repository.revoke(tokenHash.trim());
    }

    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        return repository.revokeFamily(familyId.trim());
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

    public record RefreshTokenRecord(String tokenHash, int userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}

