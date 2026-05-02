package com.nowcoder.community.user.infrastructure.persistence;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.user.domain.model.RefreshTokenSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * refresh token 存储仓库（SSOT=DB）。
 *
 * <p>说明：只存 token_hash（SHA-256 hex），避免明文凭据落库。</p>
 */
@Repository
public class MyBatisRefreshTokenSessionRepository implements com.nowcoder.community.user.domain.repository.RefreshTokenSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public MyBatisRefreshTokenSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenHash) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        String family = familyId.trim();
        int updated = jdbcTemplate.update(
                """
                        insert into auth_refresh_token(token_hash, user_id, family_id, expires_at, revoked_at)
                        select ?, ?, ?, ?, null
                        where not exists (
                          select 1 from auth_refresh_token_family_revocation where family_id = ?
                        )
                        on duplicate key update
                          user_id = values(user_id),
                          family_id = values(family_id),
                          expires_at = values(expires_at),
                          revoked_at = null
                """,
                tokenHash.trim(),
                BinaryUuidCodec.toBytes(userId),
                family,
                Timestamp.from(expiresAt),
                family
        );
        if (updated <= 0) {
            throw new IllegalStateException("refresh token family 已被撤销");
        }
    }

    @Override
    public RefreshTokenSession find(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return null;
        }
        return jdbcTemplate.query(
                """
                        select token_hash, user_id, family_id, expires_at, revoked_at
                        from auth_refresh_token
                        where token_hash = ?
                        """,
                rs -> {
                    return mapOneOrNull(rs);
                },
                tokenHash.trim()
        );
    }

    public RefreshTokenSession consumeActive(String tokenHash, Instant now) {
        if (!StringUtils.hasText(tokenHash) || now == null) {
            return null;
        }
        RefreshTokenSession record = find(tokenHash);
        if (record == null || record.revokedAt() != null || record.expiresAt() == null || !record.expiresAt().isAfter(now)) {
            return null;
        }
        int updated = jdbcTemplate.update(
                """
                        update auth_refresh_token
                        set revoked_at = ?
                        where token_hash = ?
                          and revoked_at is null
                          and expires_at > ?
                        """,
                Timestamp.from(now),
                tokenHash.trim(),
                Timestamp.from(now)
        );
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
    public void revoke(String tokenHash) {
        if (!StringUtils.hasText(tokenHash)) {
            return;
        }
        jdbcTemplate.update(
                """
                        update auth_refresh_token
                        set revoked_at = now()
                        where token_hash = ?
                          and revoked_at is null
                        """,
                tokenHash.trim()
        );
    }

    @Override
    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        String family = familyId.trim();
        jdbcTemplate.update(
                """
                        insert into auth_refresh_token_family_revocation(family_id, revoked_at)
                        values (?, now())
                        on duplicate key update revoked_at = values(revoked_at)
                        """,
                family
        );
        return jdbcTemplate.update(
                """
                        update auth_refresh_token
                        set revoked_at = now()
                        where family_id = ?
                          and revoked_at is null
                        """,
                family
        );
    }

    @Override
    public int revokeByUserId(UUID userId) {
        if (userId == null) {
            return 0;
        }
        jdbcTemplate.update(
                """
                        insert into auth_refresh_token_family_revocation(family_id, revoked_at)
                        select distinct family_id, now()
                        from auth_refresh_token
                        where user_id = ?
                        on duplicate key update revoked_at = values(revoked_at)
                        """,
                BinaryUuidCodec.toBytes(userId)
        );
        return jdbcTemplate.update(
                """
                        update auth_refresh_token
                        set revoked_at = now()
                        where user_id = ?
                          and revoked_at is null
                        """,
                BinaryUuidCodec.toBytes(userId)
        );
    }

    @Override
    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return jdbcTemplate.update(
                "delete from auth_refresh_token where expires_at <= ?",
                Timestamp.from(cutoff)
        );
    }

    private RefreshTokenSession mapOneOrNull(ResultSet rs) throws java.sql.SQLException {
        if (rs == null || !rs.next()) {
            return null;
        }
        String tokenHash = rs.getString("token_hash");
        UUID userId = BinaryUuidCodec.fromBytes(rs.getBytes("user_id"));
        String familyId = rs.getString("family_id");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new RefreshTokenSession(
                tokenHash,
                userId,
                familyId,
                expiresAt == null ? null : expiresAt.toInstant(),
                revokedAt == null ? null : revokedAt.toInstant()
        );
    }

}
