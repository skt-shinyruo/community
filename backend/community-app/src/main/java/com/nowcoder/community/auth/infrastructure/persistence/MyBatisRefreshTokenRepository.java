package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.RefreshTokenSession;
import com.nowcoder.community.auth.domain.model.RefreshTokenSessionState;
import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.auth.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import com.nowcoder.community.auth.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
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

    private static final int CLEANUP_BATCH_SIZE = 500;
    private static final int MAX_CLEANUP_BATCHES = 200;

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
        String family = familyId.trim();
        lockFamily(family, expiresAt);
        storeWhileFamilyLocked(refreshToken, userId, family, securityVersionAtIssue, expiresAt);
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
    public StoredRefreshToken beginRotation(
            String refreshToken,
            Instant pendingExpiresAt,
            UUID rotationLeaseId
    ) {
        if (!StringUtils.hasText(refreshToken) || pendingExpiresAt == null || rotationLeaseId == null) {
            return null;
        }
        String tokenHash = sha256Hex(refreshToken);
        RefreshTokenSession snapshot = findSessionByHash(tokenHash);
        if (snapshot == null || !StringUtils.hasText(snapshot.familyId())) {
            return null;
        }
        String family = snapshot.familyId().trim();
        lockFamily(family, snapshot.expiresAt());
        RefreshTokenSession locked = findSessionByHashForUpdate(tokenHash);
        if (locked == null || !family.equals(locked.familyId())) {
            return null;
        }
        Instant now = Instant.now();
        mapper.recoverExpiredPending(tokenHash, now);
        if (mapper.beginRotation(tokenHash, pendingExpiresAt, now, rotationLeaseId) <= 0) {
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
            Instant replacementExpiresAt,
            UUID rotationLeaseId
    ) {
        if (!StringUtils.hasText(pendingRefreshToken)
                || !StringUtils.hasText(replacementRefreshToken)
                || userId == null
                || !StringUtils.hasText(familyId)
                || securityVersionAtIssue < 0
                || replacementExpiresAt == null
                || rotationLeaseId == null) {
            return false;
        }
        String pendingHash = sha256Hex(pendingRefreshToken);
        RefreshTokenSession snapshot = findSessionByHash(pendingHash);
        String family = familyId.trim();
        if (snapshot == null || !family.equals(snapshot.familyId())) {
            return false;
        }
        lockFamily(family, replacementExpiresAt);
        RefreshTokenSession ownedPending = findSessionByHashForUpdate(pendingHash);
        Instant now = Instant.now();
        if (ownedPending == null
                || !family.equals(ownedPending.familyId())
                || ownedPending.state() != RefreshTokenSessionState.PENDING_ROTATION
                || !rotationLeaseId.equals(ownedPending.rotationLeaseId())
                || ownedPending.expiresAt() == null
                || !ownedPending.expiresAt().isAfter(now)
                || ownedPending.pendingExpiresAt() == null
                || !ownedPending.pendingExpiresAt().isAfter(now)) {
            return false;
        }
        storeWhileFamilyLocked(
                replacementRefreshToken,
                userId,
                family,
                securityVersionAtIssue,
                replacementExpiresAt
        );
        int updated = mapper.finishPendingRotation(
                pendingHash,
                userId,
                family,
                securityVersionAtIssue,
                now,
                rotationLeaseId
        );
        if (updated <= 0) {
            throw new IllegalStateException("refresh token pending rotation 不存在或已失效");
        }
        return true;
    }

    @Override
    public boolean rollbackPendingRotation(String refreshToken, UUID rotationLeaseId) {
        if (!StringUtils.hasText(refreshToken) || rotationLeaseId == null) {
            return false;
        }
        return mapper.rollbackPendingRotation(sha256Hex(refreshToken), rotationLeaseId) > 0;
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
        lockFamily(family, Instant.now().plusSeconds(1));
        revokeFamilyWhileLocked(family);
    }

    private void revokeFamilyWhileLocked(String family) {
        mapper.upsertFamilyRevocation(family);
        mapper.revokeFamilyTokens(family);
    }

    @Override
    public boolean revokeFamilyByPresentedToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return false;
        }
        String tokenHash = sha256Hex(refreshToken);
        RefreshTokenSession snapshot = findSessionByHash(tokenHash);
        if (snapshot == null || !StringUtils.hasText(snapshot.familyId())) {
            return false;
        }
        String family = snapshot.familyId().trim();
        lockFamily(family, snapshot.expiresAt());
        RefreshTokenSession session = findSessionByHashForUpdate(tokenHash);
        if (session == null || !family.equals(session.familyId())) {
            return false;
        }
        revokeFamilyWhileLocked(family);
        return true;
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        int deletedTokens = drainCleanupBatches(() -> mapper.deleteExpiredBefore(cutoff));
        drainCleanupBatches(() -> mapper.deleteExpiredFamilyRevocationsBefore(cutoff));
        drainCleanupBatches(() -> mapper.deleteExpiredFamilyLocksBefore(cutoff));
        return deletedTokens;
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

    private RefreshTokenSession findSessionByHashForUpdate(String tokenHash) {
        RefreshTokenSessionDataObject row = mapper.selectByTokenHashForUpdate(tokenHash);
        return row == null ? null : row.toDomain();
    }

    private void lockFamily(String familyId, Instant retainUntil) {
        Instant minimumRetention = Instant.now().plusSeconds(1);
        Instant retention = retainUntil != null && retainUntil.isAfter(minimumRetention)
                ? retainUntil
                : minimumRetention;
        mapper.ensureFamilyLock(familyId, retention);
        if (mapper.selectFamilyLockForUpdate(familyId) == null) {
            throw new IllegalStateException("refresh token family lock 不存在");
        }
    }

    private void storeWhileFamilyLocked(
            String refreshToken,
            UUID userId,
            String familyId,
            long securityVersionAtIssue,
            Instant expiresAt
    ) {
        int updated;
        try {
            updated = mapper.storeIfFamilyActive(
                    sha256Hex(refreshToken),
                    userId,
                    familyId,
                    securityVersionAtIssue,
                    expiresAt
            );
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("refresh token 已存在", exception);
        }
        if (updated <= 0) {
            throw new IllegalStateException("refresh token family 已被撤销");
        }
    }

    private int drainCleanupBatches(java.util.function.IntSupplier deleteBatch) {
        long deletedTotal = 0L;
        for (int batch = 0; batch < MAX_CLEANUP_BATCHES; batch++) {
            int deleted = deleteBatch.getAsInt();
            if (deleted <= 0) {
                break;
            }
            deletedTotal += deleted;
            if (deleted < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        return deletedTotal > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) deletedTotal;
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
                session.expiresAt(),
                session.rotationLeaseId()
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
