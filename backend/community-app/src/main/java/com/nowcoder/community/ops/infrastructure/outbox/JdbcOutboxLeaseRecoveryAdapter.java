package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.ops.application.OutboxLeaseRecoveryPort;
import com.nowcoder.community.ops.application.result.OutboxLeaseRecoveryResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

@Repository
public class JdbcOutboxLeaseRecoveryAdapter implements OutboxLeaseRecoveryPort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcOutboxLeaseRecoveryAdapter(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public OutboxLeaseRecoveryResult recoverExpiredLeases(int limit) {
        int safeLimit = Math.min(500, Math.max(1, limit));
        Timestamp now = Timestamp.from(clock.instant());
        List<ExpiredLeaseRow> rows = jdbcTemplate.query(
                "select id, processing_lease_until from outbox_event "
                        + "where status = ? and processing_lease_until is not null "
                        + "and processing_lease_until <= ? "
                        + "order by id asc limit ?",
                (rs, rowNum) -> new ExpiredLeaseRow(
                        rs.getBytes("id"),
                        rs.getTimestamp("processing_lease_until")
                ),
                OutboxEventStatus.PROCESSING,
                now,
                safeLimit
        );
        int recovered = 0;
        for (ExpiredLeaseRow row : rows) {
            recovered += jdbcTemplate.update(
                    "update outbox_event set status = ?, lease_token = null, processing_lease_until = null, "
                            + "next_retry_at = ?, updated_at = ? where id = ? and status = ? "
                            + "and processing_lease_until = ? and processing_lease_until <= ?",
                    OutboxEventStatus.PENDING,
                    now,
                    now,
                    row.id(),
                    OutboxEventStatus.PROCESSING,
                    row.leaseUntil(),
                    now
            );
        }
        return new OutboxLeaseRecoveryResult(rows.size(), recovered);
    }

    private record ExpiredLeaseRow(byte[] id, Timestamp leaseUntil) {
    }
}
