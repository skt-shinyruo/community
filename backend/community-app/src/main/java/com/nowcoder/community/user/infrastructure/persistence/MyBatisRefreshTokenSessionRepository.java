package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.user.domain.model.RefreshTokenSession;
import com.nowcoder.community.user.infrastructure.persistence.dataobject.RefreshTokenSessionDataObject;
import com.nowcoder.community.user.infrastructure.persistence.mapper.RefreshTokenSessionMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * refresh token 存储仓库（SSOT=DB）。
 *
 * <p>说明：只存 token_hash（SHA-256 hex），避免明文凭据落库。</p>
 */
@Repository
public class MyBatisRefreshTokenSessionRepository implements com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository {

    private final RefreshTokenSessionMapper mapper;

    public MyBatisRefreshTokenSessionRepository(RefreshTokenSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenHash) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        String family = familyId.trim();
        int updated = mapper.storeIfFamilyActive(tokenHash.trim(), userId, family, expiresAt);
        if (updated <= 0) {
            throw new IllegalStateException("refresh token family 已被撤销");
        }
    }

    @Override
    public RefreshTokenSession find(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return null;
        }
        RefreshTokenSessionDataObject row = mapper.selectByTokenHash(tokenHash.trim());
        return row == null ? null : row.toDomain();
    }

    public RefreshTokenSession consumeActive(String tokenHash, Instant now) {
        if (!StringUtils.hasText(tokenHash) || now == null) {
            return null;
        }
        RefreshTokenSession record = find(tokenHash);
        if (record == null || record.revokedAt() != null || record.expiresAt() == null || !record.expiresAt().isAfter(now)) {
            return null;
        }
        int updated = mapper.consumeActive(tokenHash.trim(), now);
        if (updated <= 0) {
            return null;
        }
        return record;
    }

    @Override
    public RefreshTokenSession consumeActive(String tokenHash) {
        return consumeActive(tokenHash, Instant.now());
    }

    @Override
    public RefreshTokenSession beginRotation(String tokenHash, Instant pendingExpiresAt) {
        if (!StringUtils.hasText(tokenHash) || pendingExpiresAt == null) {
            return null;
        }
        String token = tokenHash.trim();
        Instant now = Instant.now();
        mapper.recoverExpiredPending(token, now);
        int updated = mapper.beginRotation(token, pendingExpiresAt, now);
        if (updated <= 0) {
            return null;
        }
        return find(token);
    }

    @Override
    public boolean finishRotation(
            String pendingTokenHash,
            String replacementTokenHash,
            UUID userId,
            String familyId,
            Instant replacementExpiresAt
    ) {
        if (!StringUtils.hasText(pendingTokenHash)
                || !StringUtils.hasText(replacementTokenHash)
                || userId == null
                || !StringUtils.hasText(familyId)
                || replacementExpiresAt == null) {
            return false;
        }
        String pendingToken = pendingTokenHash.trim();
        String replacementToken = replacementTokenHash.trim();
        String family = familyId.trim();
        store(replacementToken, userId, family, replacementExpiresAt);
        int updated = mapper.finishPendingRotation(pendingToken, userId, family, Instant.now());
        if (updated <= 0) {
            throw new IllegalStateException("refresh token pending rotation 不存在或已失效");
        }
        return true;
    }

    @Override
    public boolean rollbackPendingRotation(String pendingTokenHash) {
        if (!StringUtils.hasText(pendingTokenHash)) {
            return false;
        }
        return mapper.rollbackPendingRotation(pendingTokenHash.trim()) > 0;
    }

    @Override
    public void revoke(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return;
        }
        mapper.revoke(tokenHash.trim());
    }

    @Override
    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        String family = familyId.trim();
        mapper.upsertFamilyRevocation(family);
        return mapper.revokeFamilyTokens(family);
    }

    @Override
    public int revokeByUserId(UUID userId) {
        if (userId == null) {
            return 0;
        }
        mapper.upsertUserFamilyRevocations(userId);
        return mapper.revokeUserTokens(userId);
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return mapper.deleteExpiredBefore(cutoff);
    }

}
