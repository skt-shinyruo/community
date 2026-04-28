package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * refresh token DB 存储（SSOT=user 模块 MySQL）：
 * - auth 模块不直连 MySQL，通过 user owner-domain API 托管会话状态
 * - 仅存 token_hash（SHA-256 hex），避免明文凭据落库
 */
@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "db")
public class DbRefreshTokenRepository implements RefreshTokenRepository {

    private final UserRefreshTokenSessionActionApi refreshTokenSessionActionApi;
    private final UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi;
    private final JwtProperties jwtProperties;

    public DbRefreshTokenRepository(
            UserRefreshTokenSessionActionApi refreshTokenSessionActionApi,
            UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi
    ) {
        this(refreshTokenSessionActionApi, refreshTokenSessionQueryApi, new JwtProperties());
    }

    @Autowired
    public DbRefreshTokenRepository(
            UserRefreshTokenSessionActionApi refreshTokenSessionActionApi,
            UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenSessionActionApi = refreshTokenSessionActionApi;
        this.refreshTokenSessionQueryApi = refreshTokenSessionQueryApi;
        this.jwtProperties = jwtProperties == null ? new JwtProperties() : jwtProperties;
    }

    @Override
    public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        refreshTokenSessionActionApi.store(sha256Hex(refreshToken), userId, familyId, expiresAt);
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionView record = refreshTokenSessionQueryApi.find(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String tokenHash = sha256Hex(refreshToken);
        RefreshTokenSessionView record = refreshTokenSessionActionApi.consume(tokenHash);
        if (record != null) {
            return toStoredRefreshToken(refreshToken, record);
        }

        // Consume failed: try to detect suspicious reuse of a revoked token.
        RefreshTokenSessionView found = refreshTokenSessionQueryApi.find(tokenHash);
        maybeRevokeFamilyOnReuse(found);
        return null;
    }

    private void maybeRevokeFamilyOnReuse(RefreshTokenSessionView record) {
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

    private StoredRefreshToken toStoredRefreshToken(String refreshToken, RefreshTokenSessionView record) {
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
        refreshTokenSessionActionApi.revoke(sha256Hex(refreshToken));
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        refreshTokenSessionActionApi.revokeFamily(familyId.trim());
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
