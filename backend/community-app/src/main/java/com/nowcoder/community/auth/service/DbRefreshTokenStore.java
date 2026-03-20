package com.nowcoder.community.auth.service;

import com.nowcoder.community.user.session.RefreshTokenSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * refresh token DB 存储（SSOT=user 模块 MySQL）：
 * - auth 模块不直连 MySQL，通过调用 user 模块内部 API 托管会话状态
 * - 仅存 token_hash（SHA-256 hex），避免明文凭据落库
 */
@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "db")
public class DbRefreshTokenStore implements RefreshTokenStore {

    private final RefreshTokenSessionService refreshTokenSessionService;

    public DbRefreshTokenStore(RefreshTokenSessionService refreshTokenSessionService) {
        this.refreshTokenSessionService = refreshTokenSessionService;
    }

    @Override
    public void store(String refreshToken, int userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId <= 0 || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        refreshTokenSessionService.store(sha256Hex(refreshToken), userId, familyId, expiresAt);
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionService.RefreshTokenRecord record = refreshTokenSessionService.find(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionService.RefreshTokenRecord record = refreshTokenSessionService.consume(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    private StoredRefreshToken toStoredRefreshToken(String refreshToken, RefreshTokenSessionService.RefreshTokenRecord record) {
        if (record == null) {
            return null;
        }
        if (record.revokedAt() != null) {
            return null;
        }
        if (record.expiresAt() == null) {
            return null;
        }
        return new StoredRefreshToken(refreshToken, record.userId(), record.familyId(), record.expiresAt());
    }

    @Override
    public void revoke(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        refreshTokenSessionService.revoke(sha256Hex(refreshToken));
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        refreshTokenSessionService.revokeFamily(familyId.trim());
    }

    private String sha256Hex(String value) {
        String s = value == null ? "" : value.trim();
        if (s.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
