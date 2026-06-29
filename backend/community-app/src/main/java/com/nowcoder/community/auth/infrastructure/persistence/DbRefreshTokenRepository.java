package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RefreshTokenSessionPort;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
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

    private final RefreshTokenSessionPort refreshTokenSessionPort;

    public DbRefreshTokenRepository(RefreshTokenSessionPort refreshTokenSessionPort) {
        this.refreshTokenSessionPort = refreshTokenSessionPort;
    }

    @Override
    public void store(String refreshToken, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(refreshToken) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        refreshTokenSessionPort.store(sha256Hex(refreshToken), userId, familyId, expiresAt);
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionPort.RefreshTokenSession record = refreshTokenSessionPort.find(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionPort.RefreshTokenSession record = refreshTokenSessionPort.consume(sha256Hex(refreshToken));
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt) {
        if (!StringUtils.hasText(refreshToken) || pendingExpiresAt == null) {
            return null;
        }
        RefreshTokenSessionPort.RefreshTokenSession record = refreshTokenSessionPort.beginRotation(
                sha256Hex(refreshToken),
                pendingExpiresAt
        );
        return toStoredRefreshToken(refreshToken, record);
    }

    @Override
    public boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        if (!StringUtils.hasText(pendingRefreshToken)
                || !StringUtils.hasText(replacementRefreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || replacementExpiresAt == null) {
            return false;
        }
        return refreshTokenSessionPort.finishRotation(
                sha256Hex(pendingRefreshToken),
                sha256Hex(replacementRefreshToken),
                userId,
                familyId.trim(),
                replacementExpiresAt
        );
    }

    @Override
    public boolean rollbackPendingRotation(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return false;
        }
        return refreshTokenSessionPort.rollbackPendingRotation(sha256Hex(refreshToken));
    }

    @Override
    public RevokedRefreshToken findRevoked(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        RefreshTokenSessionPort.RefreshTokenSession record = refreshTokenSessionPort.find(sha256Hex(refreshToken));
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
        refreshTokenSessionPort.revoke(sha256Hex(refreshToken));
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        refreshTokenSessionPort.revokeFamily(familyId.trim());
    }

    private StoredRefreshToken toStoredRefreshToken(String refreshToken, RefreshTokenSessionPort.RefreshTokenSession record) {
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
