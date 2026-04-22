package com.nowcoder.community.user.session;

import com.nowcoder.community.common.id.BinaryUuidCodec;
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
public class RefreshTokenSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void store(String tokenHash, UUID userId, String familyId, Instant expiresAt) {
        if (!StringUtils.hasText(tokenHash) || userId == null || !StringUtils.hasText(familyId) || expiresAt == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        insert into auth_refresh_token(token_hash, user_id, family_id, expires_at, revoked_at)
                        values (?, ?, ?, ?, null)
                        on duplicate key update
                          user_id = values(user_id),
                          family_id = values(family_id),
                          expires_at = values(expires_at),
                          revoked_at = null
                        """,
                tokenHash.trim(),
                BinaryUuidCodec.toBytes(userId),
                familyId.trim(),
                Timestamp.from(expiresAt)
        );
    }

    public RefreshTokenRecord find(String tokenHash) {
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

    public RefreshTokenRecord consumeActive(String tokenHash, Instant now) {
        if (!StringUtils.hasText(tokenHash) || now == null) {
            return null;
        }
        RefreshTokenRecord record = find(tokenHash);
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

    public int revokeFamily(String familyId) {
        if (!StringUtils.hasText(familyId)) {
            return 0;
        }
        return jdbcTemplate.update(
                """
                        update auth_refresh_token
                        set revoked_at = now()
                        where family_id = ?
                          and revoked_at is null
                        """,
                familyId.trim()
        );
    }

    public int deleteExpiredBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        return jdbcTemplate.update(
                "delete from auth_refresh_token where expires_at <= ?",
                Timestamp.from(cutoff)
        );
    }

    private RefreshTokenRecord mapOneOrNull(ResultSet rs) throws java.sql.SQLException {
        if (rs == null || !rs.next()) {
            return null;
        }
        String tokenHash = rs.getString("token_hash");
        UUID userId = BinaryUuidCodec.fromBytes(rs.getBytes("user_id"));
        String familyId = rs.getString("family_id");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        return new RefreshTokenRecord(
                tokenHash,
                userId,
                familyId,
                expiresAt == null ? null : expiresAt.toInstant(),
                revokedAt == null ? null : revokedAt.toInstant()
        );
    }

    public record RefreshTokenRecord(String tokenHash, UUID userId, String familyId, Instant expiresAt, Instant revokedAt) {
    }
}
