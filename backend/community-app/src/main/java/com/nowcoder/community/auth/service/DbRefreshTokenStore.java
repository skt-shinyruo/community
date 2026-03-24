package com.nowcoder.community.auth.service;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.user.session.RefreshTokenSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
    private final JwtProperties jwtProperties;

    public DbRefreshTokenStore(RefreshTokenSessionService refreshTokenSessionService) {
        this(refreshTokenSessionService, new JwtProperties());
    }

    @Autowired
    public DbRefreshTokenStore(RefreshTokenSessionService refreshTokenSessionService, JwtProperties jwtProperties) {
        this.refreshTokenSessionService = refreshTokenSessionService;
        this.jwtProperties = jwtProperties == null ? new JwtProperties() : jwtProperties;
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
        String tokenHash = sha256Hex(refreshToken);
        RefreshTokenSessionService.RefreshTokenRecord record = refreshTokenSessionService.consume(tokenHash);
        if (record != null) {
            return toStoredRefreshToken(refreshToken, record);
        }

        // Consume failed: try to detect suspicious reuse of a revoked token.
        RefreshTokenSessionService.RefreshTokenRecord found = refreshTokenSessionService.find(tokenHash);
        maybeRevokeFamilyOnReuse(found);
        return null;
    }

    private void maybeRevokeFamilyOnReuse(RefreshTokenSessionService.RefreshTokenRecord record) {
        if (record == null) {
            return;
        }
        Instant revokedAt = record.revokedAt();
        if (revokedAt == null) {
            return;
        }
        Instant now = Instant.now();
        Instant expiresAt = record.expiresAt();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return;
        }

        long graceSeconds = jwtProperties.getRefreshReuseGraceSeconds();
        if (graceSeconds < 0) {
            graceSeconds = 0;
        }
        if (Duration.between(revokedAt, now).compareTo(Duration.ofSeconds(graceSeconds)) > 0) {
            revokeFamily(record.familyId());
        }
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
