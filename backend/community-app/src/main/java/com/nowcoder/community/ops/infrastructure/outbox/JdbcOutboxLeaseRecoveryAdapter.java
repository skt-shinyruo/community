package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.OutboxLeaseRecoveryPort;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcOutboxLeaseRecoveryAdapter implements OutboxLeaseRecoveryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxLeaseRecoveryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OutboxLeaseRecoveryResult recoverExpiredLeases(int limit) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        Timestamp now = Timestamp.from(Instant.now());
        List<byte[]> ids = jdbcTemplate.query(
                "select id from outbox_event "
                        + "where status = ? and next_retry_at is not null and next_retry_at <= ? "
                        + "order by id asc limit ?",
                (rs, rowNum) -> rs.getBytes("id"),
                OutboxEventStatus.PROCESSING,
                now,
                safeLimit
        );
        int recovered = 0;
        for (byte[] id : ids) {
            recovered += jdbcTemplate.update(
                    "update outbox_event set status = ?, next_retry_at = ?, updated_at = ? "
                            + "where id = ? and status = ? and next_retry_at is not null and next_retry_at <= ?",
                    OutboxEventStatus.PENDING,
                    now,
                    now,
                    id,
                    OutboxEventStatus.PROCESSING,
                    now
            );
        }
        return new OutboxLeaseRecoveryResult(ids.size(), recovered);
    }
}
