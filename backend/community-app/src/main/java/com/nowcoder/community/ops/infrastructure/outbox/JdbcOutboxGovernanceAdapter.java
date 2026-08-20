package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.ops.application.OutboxGovernancePort;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import com.nowcoder.community.ops.application.result.OutboxBacklogResult;
import com.nowcoder.community.ops.application.result.OutboxEventResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOutboxGovernanceAdapter implements OutboxGovernancePort {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcOutboxEventStore store;
    private final Clock clock;

    public JdbcOutboxGovernanceAdapter(JdbcTemplate jdbcTemplate, JdbcOutboxEventStore store, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public List<OutboxBacklogResult> listBacklog() {
        return store.countBacklogByTopicAndStatus().stream()
                .map(row -> new OutboxBacklogResult(row.topic(), row.status(), row.count()))
                .toList();
    }

    @Override
    public List<OutboxEventResult> findEvents(FindOutboxEventsCommand command) {
        FindOutboxEventsCommand c = command == null
                ? new FindOutboxEventsCommand(null, null, null, null, null, 50)
                : command.normalized();
        StringBuilder sql = new StringBuilder(
                "select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, " +
                        "last_error, trace_id, traceparent, created_at, updated_at from outbox_event where 1 = 1"
        );
        List<Object> args = new java.util.ArrayList<>();
        if (c.status() != null) {
            sql.append(" and status = ?");
            args.add(c.status());
        }
        if (c.topic() != null) {
            sql.append(" and topic = ?");
            args.add(c.topic());
        }
        if (c.eventId() != null) {
            sql.append(" and event_id = ?");
            args.add(c.eventId());
        }
        if (c.createdFrom() != null) {
            sql.append(" and created_at >= ?");
            args.add(Timestamp.from(c.createdFrom()));
        }
        if (c.createdTo() != null) {
            sql.append(" and created_at <= ?");
            args.add(Timestamp.from(c.createdTo()));
        }
        sql.append(" order by id asc limit ?");
        args.add(c.limit());
        return jdbcTemplate.query(sql.toString(), eventRowMapper(), args.toArray());
    }

    @Override
    public Optional<OutboxEventResult> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        List<OutboxEventResult> rows = jdbcTemplate.query(
                "select id, event_id, topic, event_key, payload, status, retry_count, next_retry_at, " +
                        "last_error, trace_id, traceparent, created_at, updated_at from outbox_event where id = ?",
                eventRowMapper(),
                BinaryUuidCodec.toBytes(id)
        );
        return rows.stream().findFirst();
    }

    @Override
    public boolean requeueDead(UUID id, String reason) {
        return store.requeueDeadForReplay(id, clock.instant(), reason);
    }

    private static RowMapper<OutboxEventResult> eventRowMapper() {
        return (rs, rowNum) -> {
            Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return new OutboxEventResult(
                    BinaryUuidCodec.fromBytes(rs.getBytes("id")),
                    rs.getString("event_id"),
                    rs.getString("topic"),
                    rs.getString("event_key"),
                    rs.getString("payload"),
                    rs.getString("status"),
                    rs.getInt("retry_count"),
                    nextRetryAt == null ? null : nextRetryAt.toInstant(),
                    rs.getString("last_error"),
                    rs.getString("trace_id"),
                    rs.getString("traceparent"),
                    createdAt == null ? null : createdAt.toInstant(),
                    updatedAt == null ? null : updatedAt.toInstant()
            );
        };
    }
}
