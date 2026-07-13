package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.id.UuidV7Generator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * MySQL 幂等存储实现（SSOT=DB）：
 * - 通过唯一键 (operation, user_id, idem_key) 实现 insert-first 的幂等锁
 * - 支持 PROCESSING/SUCCESS 状态与过期时间（TTL）字段
 *
 * <p>说明：该实现用于把 Redis 抖动从关键写链路中隔离出去；Redis 仅作为可选加速层。</p>
 */
public class JdbcIdempotencyStore implements IdempotencyStore {

    private static final String STATUS_PROCESSING = "P";
    private static final String STATUS_SUCCESS = "S";

    private final JdbcTemplate jdbcTemplate;
    private final UuidV7Generator idGenerator;

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new UuidV7Generator());
    }

    public JdbcIdempotencyStore(JdbcTemplate jdbcTemplate, UuidV7Generator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Override
    public boolean tryAcquireProcessing(String operation, UUID userId, String key, String requestHash, Duration ttl) {
        String op = normalizeOp(operation);
        String k = normalizeKey(key);
        String hash = normalizeHash(requestHash);
        if (userId == null) {
            throw new IllegalArgumentException("userId is invalid");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        Instant now = Instant.now();
        Timestamp processingExpiresAt = Timestamp.from(now.plus(safeTtl));

        try {
            int inserted = jdbcTemplate.update(
                    """
                            insert into http_idempotency(id, operation, user_id, idem_key, request_hash, status, processing_expires_at)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    BinaryUuidCodec.toBytes(idGenerator.next()),
                    op,
                    BinaryUuidCodec.toBytes(userId),
                    k,
                    hash,
                    STATUS_PROCESSING,
                    processingExpiresAt
            );
            return inserted > 0;
        } catch (DuplicateKeyException e) {
            Timestamp cutoff = Timestamp.from(now);
            int updated = jdbcTemplate.update(
                    """
                            update http_idempotency
                            set status = ?,
                                request_hash = ?,
                                processing_expires_at = ?,
                                success_expires_at = null,
                                response_json = null,
                                updated_at = now()
                            where operation = ?
                              and user_id = ?
                              and idem_key = ?
                              and (
                                (status = ? and processing_expires_at is not null and processing_expires_at < ?)
                                or
                                (status = ? and success_expires_at is not null and success_expires_at < ?)
                              )
                            """,
                    STATUS_PROCESSING,
                    hash,
                    processingExpiresAt,
                    op,
                    BinaryUuidCodec.toBytes(userId),
                    k,
                    STATUS_PROCESSING,
                    cutoff,
                    STATUS_SUCCESS,
                    cutoff
            );
            return updated > 0;
        }
    }

    @Override
    public Entry get(String operation, UUID userId, String key) {
        String op = normalizeOp(operation);
        String k = normalizeKey(key);
        if (userId == null) {
            return null;
        }

        Instant now = Instant.now();
        return jdbcTemplate.query(
                """
                        select status, request_hash, response_json, processing_expires_at, success_expires_at
                        from http_idempotency
                        where operation = ?
                          and user_id = ?
                          and idem_key = ?
                        """,
                (ResultSetExtractor<Entry>) rs -> mapEntryOrNull(rs, op, userId, k, now),
                op,
                BinaryUuidCodec.toBytes(userId),
                k
        );
    }

    @Override
    public void saveSuccess(String operation, UUID userId, String key, String requestHash, String successJson, Duration ttl) {
        String op = normalizeOp(operation);
        String k = normalizeKey(key);
        String hash = normalizeHash(requestHash);
        if (userId == null) {
            throw new IllegalArgumentException("userId is invalid");
        }
        Duration safeTtl = ttl == null ? Duration.ofHours(24) : ttl;
        Instant now = Instant.now();
        Timestamp successExpiresAt = Timestamp.from(now.plus(safeTtl));
        String json = successJson == null ? "null" : successJson;

        jdbcTemplate.update(
                """
                        insert into http_idempotency(
                          id, operation, user_id, idem_key, status,
                          request_hash, response_json, success_expires_at, processing_expires_at
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, null)
                        on duplicate key update
                          status = values(status),
                          request_hash = values(request_hash),
                          response_json = values(response_json),
                          success_expires_at = values(success_expires_at),
                          processing_expires_at = null,
                          updated_at = now()
                        """,
                BinaryUuidCodec.toBytes(idGenerator.next()),
                op,
                BinaryUuidCodec.toBytes(userId),
                k,
                STATUS_SUCCESS,
                hash,
                json,
                successExpiresAt
        );
    }

    @Override
    public void extendProcessing(String operation, UUID userId, String key, Duration ttl) {
        String op = normalizeOp(operation);
        String k = normalizeKey(key);
        if (userId == null) {
            throw new IllegalArgumentException("userId is invalid");
        }
        Duration safeTtl = ttl == null ? Duration.ofSeconds(30) : ttl;
        Timestamp processingExpiresAt = Timestamp.from(Instant.now().plus(safeTtl));
        jdbcTemplate.update(
                """
                        update http_idempotency
                        set processing_expires_at = ?,
                            updated_at = now()
                        where operation = ?
                          and user_id = ?
                          and idem_key = ?
                          and status = ?
                        """,
                processingExpiresAt,
                op,
                BinaryUuidCodec.toBytes(userId),
                k,
                STATUS_PROCESSING
        );
    }

    @Override
    public void delete(String operation, UUID userId, String key) {
        String op = normalizeOp(operation);
        String k = normalizeKey(key);
        if (userId == null) {
            return;
        }
        jdbcTemplate.update(
                """
                        delete from http_idempotency
                        where operation = ?
                          and user_id = ?
                          and idem_key = ?
                        """,
                op,
                BinaryUuidCodec.toBytes(userId),
                k
        );
    }

    private Entry mapEntryOrNull(ResultSet rs, String op, UUID userId, String key, Instant now) throws java.sql.SQLException {
        if (rs == null || !rs.next()) {
            return null;
        }
        String status = rs.getString("status");
        String requestHash = rs.getString("request_hash");
        Timestamp processingExpiresAt = rs.getTimestamp("processing_expires_at");
        Timestamp successExpiresAt = rs.getTimestamp("success_expires_at");
        String responseJson = rs.getString("response_json");

        if (STATUS_PROCESSING.equals(status)) {
            if (processingExpiresAt == null || now.isAfter(processingExpiresAt.toInstant())) {
                delete(op, userId, key);
                return null;
            }
            return new Entry(Status.PROCESSING, null, requestHash);
        }
        if (STATUS_SUCCESS.equals(status)) {
            if (successExpiresAt == null || now.isAfter(successExpiresAt.toInstant())) {
                delete(op, userId, key);
                return null;
            }
            return new Entry(Status.SUCCESS, responseJson == null ? "null" : responseJson, requestHash);
        }
        throw new IllegalStateException("unknown idempotency state");
    }

    private String normalizeOp(String operation) {
        if (!StringUtils.hasText(operation)) {
            throw new IllegalArgumentException("operation is blank");
        }
        return operation.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key is blank");
        }
        return key.trim();
    }

    private String normalizeHash(String requestHash) {
        return IdempotencyStore.requireRequestHash(requestHash);
    }
}
