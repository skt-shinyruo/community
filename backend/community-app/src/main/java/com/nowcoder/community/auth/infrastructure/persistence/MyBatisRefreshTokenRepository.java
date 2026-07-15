package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.RefreshTokenSession;
import com.nowcoder.community.auth.domain.model.RefreshTokenSessionState;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import com.nowcoder.community.auth.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "auth.refresh.store", havingValue = "db")
public class MyBatisRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenSessionMapper mapper;

    public MyBatisRefreshTokenRepository(RefreshTokenSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void store(
            String refreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant expiresAt
    ) {
        if (!StringUtils.hasText(refreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || securityVersionAtIssue < 0
                || expiresAt == null) {
            return;
        }
        int updated = mapper.storeIfFamilyActive(
                sha256Hex(refreshToken),
                userId,
                familyId.trim(),
                securityVersionAtIssue,
                expiresAt
        );
        if (updated <= 0) {
            throw new IllegalStateException("refresh token family 已被撤销");
        }
    }

    @Override
    public StoredRefreshToken find(String refreshToken) {
        return toStoredRefreshToken(refreshToken, findSession(refreshToken), false);
    }

    @Override
    public StoredRefreshToken consume(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        String tokenHash = sha256Hex(refreshToken);
        RefreshTokenSession session = findSessionByHash(tokenHash);
        if (session == null || session.state() != RefreshTokenSessionState.ACTIVE || session.revokedAt() != null) {
            return null;
        }
        if (mapper.consumeActive(tokenHash, Instant.now()) <= 0) {
            return null;
        }
        return toStoredRefreshToken(refreshToken, session, false);
    }

    @Override
    public StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt) {
        if (!StringUtils.hasText(refreshToken) || pendingExpiresAt == null) {
            return null;
        }
        String tokenHash = sha256Hex(refreshToken);
        Instant now = Instant.now();
        mapper.recoverExpiredPending(tokenHash, now);
        if (mapper.beginRotation(tokenHash, pendingExpiresAt, now) <= 0) {
            return null;
        }
        return toStoredRefreshToken(refreshToken, findSessionByHash(tokenHash), true);
    }

    @Override
    public boolean finishRotation(
            String pendingRefreshToken,
            String replacementRefreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant replacementExpiresAt
    ) {
        if (!StringUtils.hasText(pendingRefreshToken)
                || !StringUtils.hasText(replacementRefreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || securityVersionAtIssue < 0
                || replacementExpiresAt == null) {
            return false;
        }
        String family = familyId.trim();
        store(replacementRefreshToken, userId, family, securityVersionAtIssue, replacementExpiresAt);
        int updated = mapper.finishPendingRotation(
                sha256Hex(pendingRefreshToken),
                userId,
                family,
                securityVersionAtIssue,
                Instant.now()
        );
        if (updated <= 0) {
            throw new IllegalStateException("refresh token pending rotation 不存在或已失效");
        }
        return true;
    }

    @Override
    public boolean rollbackPendingRotation(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return false;
        }
        return mapper.rollbackPendingRotation(sha256Hex(refreshToken)) > 0;
    }

    @Override
    public RevokedRefreshToken findRevoked(String refreshToken) {
        RefreshTokenSession session = findSession(refreshToken);
        if (session == null || session.revokedAt() == null) {
            return null;
        }
        return new RevokedRefreshToken(
                normalizedToken(refreshToken),
                session.userId(),
                session.familyId(),
                session.expiresAt(),
                session.revokedAt()
        );
    }

    @Override
    public void revoke(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            mapper.revoke(sha256Hex(refreshToken));
        }
    }

    @Override
    public void revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return;
        }
        String family = familyId.trim();
        mapper.upsertFamilyRevocation(family);
        mapper.revokeFamilyTokens(family);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        return cutoff == null ? 0 : mapper.deleteExpiredBefore(cutoff);
    }

    private RefreshTokenSession findSession(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return null;
        }
        return findSessionByHash(sha256Hex(refreshToken));
    }

    private RefreshTokenSession findSessionByHash(String tokenHash) {
        RefreshTokenSessionDataObject row = mapper.selectByTokenHash(tokenHash);
        return row == null ? null : row.toDomain();
    }

    private StoredRefreshToken toStoredRefreshToken(
            String refreshToken,
            RefreshTokenSession session,
            boolean includePending
    ) {
        if (session == null
                || session.revokedAt() != null
                || session.expiresAt() == null
                || (session.state() != RefreshTokenSessionState.ACTIVE
                && !(includePending && session.state() == RefreshTokenSessionState.PENDING_ROTATION))) {
            return null;
        }
        return new StoredRefreshToken(
                normalizedToken(refreshToken),
                session.userId(),
                session.familyId(),
                session.securityVersionAtIssue(),
                session.expiresAt()
        );
    }

    private String normalizedToken(String refreshToken) {
        return refreshToken == null ? "" : refreshToken.trim();
    }

    private String sha256Hex(String value) {
        String token = normalizedToken(value);
        if (token.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
