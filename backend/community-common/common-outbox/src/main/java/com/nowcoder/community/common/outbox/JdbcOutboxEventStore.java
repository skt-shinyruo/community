package com.nowcoder.community.common.outbox;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * JDBC-based outbox store (SSOT=DB table {@code outbox_event}).
 */
public class JdbcOutboxEventStore {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventStore(JdbcTemplate jdbcTemplate) {
        if (jdbcTemplate == null) {
            throw new IllegalArgumentException("jdbcTemplate is required");
        }
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean enqueue(String eventId, String topic, String eventKey, String payload) {
        String eid = normalize(eventId, "eventId");
        String t = normalize(topic, "topic");
        String k = normalize(eventKey, "eventKey");
        String p = payload == null ? "" : payload;

        try {
            jdbcTemplate.update(
                    "insert into outbox_event(event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error) " +
                            "values (?, ?, ?, ?, ?, 0, null, null)",
                    eid,
                    t,
                    k,
                    p,
                    OutboxEventStatus.PENDING
            );
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<OutboxEvent> findDuePending(int limit, Instant now) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        Timestamp ts = Timestamp.from(now == null ? Instant.now() : now);
        return jdbcTemplate.query(
                "select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, last_error " +
                        "from outbox_event " +
                        "where status = ? and (next_retry_at is null or next_retry_at <= ?) " +
                        "order by id asc " +
                        "limit ?",
                rowMapper(),
                OutboxEventStatus.PENDING,
                ts,
                safeLimit
        );
    }

    public boolean tryClaimProcessing(long id, Instant leaseUntil, Instant now) {
        if (id <= 0) {
            return false;
        }
        Timestamp leaseTs = Timestamp.from(leaseUntil == null ? Instant.now().plusSeconds(30) : leaseUntil);
        Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
        int updated = jdbcTemplate.update(
                "update outbox_event set status = ?, next_retry_at = ?, updated_at = ? where id = ? and status = ?",
                OutboxEventStatus.PROCESSING,
                leaseTs,
                nowTs,
                id,
                OutboxEventStatus.PENDING
        );
        return updated > 0;
    }

    public void markSucceeded(long id, Instant now) {
        if (id <= 0) {
            return;
        }
        Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
        jdbcTemplate.update(
                "update outbox_event set status = ?, next_retry_at = null, last_error = null, updated_at = ? where id = ?",
                OutboxEventStatus.SUCCEEDED,
                nowTs,
                id
        );
    }

    public void markDead(long id, Instant now, String lastError) {
        if (id <= 0) {
            return;
        }
        Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
        jdbcTemplate.update(
                "update outbox_event set status = ?, last_error = ?, updated_at = ? where id = ?",
                OutboxEventStatus.DEAD,
                truncateError(lastError),
                nowTs,
                id
        );
    }

    public void markFailedAndScheduleRetry(long id, Instant now, Instant nextRetryAt, String lastError) {
        if (id <= 0) {
            return;
        }
        Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
        Timestamp nextTs = Timestamp.from(nextRetryAt == null ? nowTs.toInstant().plusSeconds(5) : nextRetryAt);
        jdbcTemplate.update(
                "update outbox_event set status = ?, retry_count = retry_count + 1, next_retry_at = ?, last_error = ?, updated_at = ? where id = ?",
                OutboxEventStatus.PENDING,
                nextTs,
                truncateError(lastError),
                nowTs,
                id
        );
    }

    /**
     * Recover events stuck in PROCESSING whose lease has expired (lease stored in {@code next_retry_at}).
     */
    public int recoverExpiredLeases(Instant now) {
        Timestamp nowTs = Timestamp.from(now == null ? Instant.now() : now);
        return jdbcTemplate.update(
                "update outbox_event set status = ?, next_retry_at = ?, updated_at = ? " +
                        "where status = ? and next_retry_at is not null and next_retry_at <= ?",
                OutboxEventStatus.PENDING,
                nowTs,
                nowTs,
                OutboxEventStatus.PROCESSING,
                nowTs
        );
    }

    private static String normalize(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String truncateError(String err) {
        if (err == null) {
            return null;
        }
        String s = err.trim();
        if (s.length() <= 255) {
            return s;
        }
        return s.substring(0, 255);
    }

    private static RowMapper<OutboxEvent> rowMapper() {
        return new RowMapper<>() {
            @Override
            public OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
                return new OutboxEvent(
                        rs.getLong("id"),
                        rs.getString("event_id"),
                        rs.getString("topic"),
                        rs.getString("event_key"),
                        rs.getString("payload"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        nextRetryAt == null ? null : nextRetryAt.toInstant(),
                        rs.getString("last_error")
                );
            }
        };
    }
}
