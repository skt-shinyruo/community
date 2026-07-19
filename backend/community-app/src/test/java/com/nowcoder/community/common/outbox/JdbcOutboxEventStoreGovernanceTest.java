package com.nowcoder.community.common.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxEventStoreGovernanceTest {

    @Test
    void governanceQueriesShouldFilterSummarizeAndRequeueDeadEvents() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);
            Instant now = Instant.parse("2026-07-07T00:00:00Z");
            UUID deadId = UUID.fromString("0197e6f0-0000-7000-8000-000000000001");
            UUID pendingId = UUID.fromString("0197e6f0-0000-7000-8000-000000000002");
            UUID deadLeaseToken = UUID.fromString("0197e6f0-0000-7000-8000-000000000003");
            insertEvent(jdbcTemplate, deadId, "e-dead:search", "eventbus.content", "post-1",
                    OutboxEventStatus.DEAD, 4, now.minusSeconds(120), now.minusSeconds(60));
            insertEvent(jdbcTemplate, pendingId, "e-pending:growth", "projection.im.policy", "post-1",
                    OutboxEventStatus.PENDING, 1, now.minusSeconds(30), now.minusSeconds(20));
            jdbcTemplate.update(
                    "update outbox_event set lease_token = ?, processing_lease_until = ? where id = ?",
                    BinaryUuidCodec.toBytes(deadLeaseToken),
                    Timestamp.from(now.plusSeconds(30)),
                    BinaryUuidCodec.toBytes(deadId)
            );

            List<OutboxEventView> deadRows = store.findEvents(new OutboxEventQuery(
                    OutboxEventStatus.DEAD,
                    "eventbus.content",
                    null,
                    now.minusSeconds(300),
                    now,
                    20
            ));

            assertThat(deadRows).hasSize(1);
            assertThat(deadRows.get(0).id()).isEqualTo(deadId);
            assertThat(deadRows.get(0).createdAt()).isEqualTo(now.minusSeconds(120));
            assertThat(deadRows.get(0).updatedAt()).isEqualTo(now.minusSeconds(60));
            assertThat(Arrays.stream(OutboxEventView.class.getRecordComponents()))
                    .extracting(component -> component.getName())
                    .doesNotContain("leaseToken", "processingLeaseUntil");

            assertThat(store.findEventById(deadId)).isPresent();
            assertThat(store.countBacklogByTopicAndStatus())
                    .extracting(row -> row.topic() + "|" + row.status() + "|" + row.count())
                    .contains("eventbus.content|DEAD|1", "projection.im.policy|PENDING|1");

            boolean requeued = store.requeueDeadForReplay(deadId, now, "fixed es mapping");

            assertThat(requeued).isTrue();
            OutboxEventView replayed = store.findEventById(deadId).orElseThrow();
            assertThat(replayed.status()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(replayed.retryCount()).isEqualTo(0);
            assertThat(replayed.nextRetryAt()).isEqualTo(now);
            assertThat(replayed.lastError()).isEqualTo("replay requested: fixed es mapping");
            assertThat(jdbcTemplate.queryForObject(
                    "select lease_token from outbox_event where id = ?",
                    byte[].class,
                    BinaryUuidCodec.toBytes(deadId)
            )).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "select processing_lease_until from outbox_event where id = ?",
                    Timestamp.class,
                    BinaryUuidCodec.toBytes(deadId)
            )).isNull();
        } finally {
            db.shutdown();
        }
    }

    private static void insertEvent(
            JdbcTemplate jdbcTemplate,
            UUID id,
            String eventId,
            String topic,
            String eventKey,
            String status,
            int retryCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, retry_count,
                          next_retry_at, last_error, trace_id, traceparent, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?)
                        """,
                BinaryUuidCodec.toBytes(id),
                eventId,
                topic,
                eventKey,
                "{\"postId\":\"post-1\"}",
                status,
                retryCount,
                "boom",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01",
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
        );
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
                        "  lease_token binary(16),\n" +
                        "  processing_lease_until timestamp,\n" +
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
        jdbcTemplate.execute("create index if not exists idx_outbox_processing_lease on outbox_event(status, processing_lease_until, id)");
        jdbcTemplate.execute("delete from outbox_event");
    }
}
