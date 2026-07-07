package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.ops.application.ProjectionLagPort;
import com.nowcoder.community.ops.application.result.ProjectionLagResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Repository
public class OutboxProjectionLagAdapter implements ProjectionLagPort {

    private final JdbcTemplate jdbcTemplate;

    public OutboxProjectionLagAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ProjectionLagResult> listProjectionLag() {
        Instant now = Instant.now();
        return jdbcTemplate.query(
                """
                        select topic, status, count(*) as row_count, min(created_at) as oldest_created_at
                        from outbox_event
                        where topic like 'projection.%'
                          and status in ('PENDING', 'PROCESSING', 'DEAD')
                        group by topic, status
                        order by topic asc, status asc
                        """,
                (rs, rowNum) -> {
                    Timestamp oldest = rs.getTimestamp("oldest_created_at");
                    Duration age = oldest == null ? Duration.ZERO : Duration.between(oldest.toInstant(), now);
                    return new ProjectionLagResult(
                            rs.getString("topic"),
                            rs.getString("status"),
                            rs.getLong("row_count"),
                            age.isNegative() ? Duration.ZERO : age
                    );
                }
        );
    }
}
