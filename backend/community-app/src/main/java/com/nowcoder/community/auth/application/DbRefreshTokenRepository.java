package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.user.api.action.UserRefreshTokenSessionActionApi;
import com.nowcoder.community.user.api.model.RefreshTokenSessionView;
import com.nowcoder.community.user.api.query.UserRefreshTokenSessionQueryApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/**
 * DB-backed refresh token sessions are owned by user; auth application coordinates through user api.*.
 */
@Component
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "db")
public class DbRefreshTokenRepository implements RefreshTokenRepository {

    private final UserRefreshTokenSessionActionApi refreshTokenSessionActionApi;
    private final UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi;

    public DbRefreshTokenRepository(
            UserRefreshTokenSessionActionApi refreshTokenSessionActionApi,
            UserRefreshTokenSessionQueryApi refreshTokenSessionQueryApi
    ) {
        this.refreshTokenSessionActionApi = refreshTokenSessionActionApi;
        this.refreshTokenSessionQueryApi = refreshTokenSessionQueryApi;
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
        RefreshTokenSessionView record = refreshTokenSessionActionApi.consume(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public RevokedRefreshToken findRevoked(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionView record = refreshTokenSessionQueryApi.find(sha256Hex(refreshToken));
        if (record == null || record.revokedAt() == null) {
            return null;
        }
        return new RevokedRefreshToken(refreshToken, record.userId(), record.familyId(), record.expiresAt(), record.revokedAt());
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

    private StoredRefreshToken toStoredRefreshToken(String refreshToken, RefreshTokenSessionView record) {
        if (record == null || record.revokedAt() != null || record.expiresAt() == null) {
            return null;
        }
        return new StoredRefreshToken(refreshToken, record.userId(), record.familyId(), record.expiresAt());
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
