package com.nowcoder.community.auth.service;

import com.nowcoder.community.user.api.internal.dto.UserInternalRefreshTokenRecordResponse;
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

    private final UserAuthAccess userAuthAccess;

    public DbRefreshTokenStore(UserAuthAccess userAuthAccess) {
        this.userAuthAccess = userAuthAccess;
    }

    @Override
    public void store(String refreshToken, int userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId <= 0 || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        userAuthAccess.storeRefreshToken(sha256Hex(refreshToken), userId, familyId, expiresAt);
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        UserInternalRefreshTokenRecordResponse record = userAuthAccess.findRefreshTokenOrNull(sha256Hex(refreshToken));
        if (record == null) {
            return null;
        }
        if (record.getRevokedAt() != null) {
            return null;
        }
        if (record.getExpiresAt() == null) {
            return null;
        }
        return new StoredRefreshToken(refreshToken, record.getUserId(), record.getFamilyId(), record.getExpiresAt());
    }

    @Override
    public void revoke(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return;
        }
        userAuthAccess.revokeRefreshToken(sha256Hex(refreshToken));
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        userAuthAccess.revokeRefreshTokenFamily(familyId.trim());
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
