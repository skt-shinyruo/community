package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.trace.TraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxEventStoreTest {

    @Test
    void enqueueAndClaimShouldWork() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);

            Instant now = Instant.parse("2026-03-14T00:00:00Z");
            SpanContext spanContext = SpanContext.create(
                    "dddddddddddddddddddddddddddddddd",
                    "1234567890abcdef",
                    TraceFlags.getSampled(),
                    TraceState.getDefault()
            );

            boolean inserted;
            try (Scope ignored = Span.wrap(spanContext).makeCurrent()) {
                inserted = store.enqueue(
                        "e-1:points",
                        "projection.points",
                        "1",
                        "{\"userId\":1,\"delta\":10}"
                );
            }
            assertThat(inserted).isTrue();

            List<OutboxEvent> due = store.findDuePending(10, now);
            assertThat(due).hasSize(1);

            OutboxEvent ev = due.get(0);
            assertThat(ev.id()).isNotNull();
            assertThat(ev.id().version()).isEqualTo(7);
            assertThat(ev.eventId()).isEqualTo("e-1:points");
            assertThat(ev.topic()).isEqualTo("projection.points");
            assertThat(ev.status()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(ev.traceId()).isEqualTo("dddddddddddddddddddddddddddddddd");
            assertThat(ev.traceparent()).isEqualTo("00-dddddddddddddddddddddddddddddddd-1234567890abcdef-01");

            Instant leaseUntil = now.plusSeconds(30);
            boolean claimed = store.tryClaimProcessing(ev.id(), leaseUntil, now);
            assertThat(claimed).isTrue();

            String status = jdbcTemplate.queryForObject(
                    "select status from outbox_event where id = ?",
                    String.class,
                    BinaryUuidCodec.toBytes(ev.id())
            );
            assertThat(status).isEqualTo(OutboxEventStatus.PROCESSING);

            Timestamp nextRetryAt = jdbcTemplate.queryForObject(
                    "select next_retry_at from outbox_event where id = ?",
                    Timestamp.class,
                    BinaryUuidCodec.toBytes(ev.id())
            );
            assertThat(nextRetryAt.toInstant()).isEqualTo(leaseUntil);

            int recovered = store.recoverExpiredLeases(now.plus(Duration.ofSeconds(31)));
            assertThat(recovered).isEqualTo(1);

            String statusAfterRecover = jdbcTemplate.queryForObject(
                    "select status from outbox_event where id = ?",
                    String.class,
                    BinaryUuidCodec.toBytes(ev.id())
            );
            assertThat(statusAfterRecover).isEqualTo(OutboxEventStatus.PENDING);
        } finally {
            TraceContext.clear();
            db.shutdown();
        }
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (\n" +
                        "  id binary(16) primary key,\n" +
                        "  event_id varchar(64) not null,\n" +
                        "  topic varchar(255) not null,\n" +
                        "  event_key varchar(255) not null,\n" +
                        "  payload clob not null,\n" +
                        "  status varchar(32) not null,\n" +
                        "  retry_count int not null default 0,\n" +
                        "  next_retry_at timestamp,\n" +
                        "  last_error varchar(512),\n" +
                        "  trace_id varchar(32) null,\n" +
                        "  traceparent varchar(128) null,\n" +
                        "  created_at timestamp default current_timestamp,\n" +
                        "  updated_at timestamp default current_timestamp,\n" +
                        "  constraint uk_outbox_event_id unique (event_id)\n" +
                        ")"
        );
        jdbcTemplate.execute("create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id)");
        jdbcTemplate.execute("delete from outbox_event");
    }
}
