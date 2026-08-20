package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxHandler;
import com.nowcoder.community.ops.application.command.FindOutboxEventsCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcOutboxGovernanceAdapterTest {

    @Test
    void adapterShouldMapStoreRowsAndReplayDeadEvents() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            UUID outboxId = UUID.fromString("0197e6f0-0000-7000-8000-000000000111");
            UUID pendingId = UUID.fromString("0197e6f0-0000-7000-8000-000000000112");
            Instant createdAt = Instant.parse("2026-07-07T00:00:00Z");
            Instant updatedAt = Instant.parse("2026-07-07T00:01:00Z");
            insertEvent(
                    jdbcTemplate,
                    outboxId,
                    "event-1",
                    "eventbus.content",
                    OutboxEventStatus.DEAD,
                    2,
                    createdAt,
                    updatedAt
            );
            insertEvent(
                    jdbcTemplate,
                    pendingId,
                    "event-2",
                    "projection.im.policy",
                    OutboxEventStatus.PENDING,
                    1,
                    createdAt.plusSeconds(30),
                    updatedAt.plusSeconds(30)
            );
            jdbcTemplate.update(
                    "update outbox_event set lease_token = ?, processing_lease_until = ? where id = ?",
                    BinaryUuidCodec.toBytes(UUID.fromString("0197e6f0-0000-7000-8000-000000000113")),
                    Timestamp.from(updatedAt.plusSeconds(60)),
                    BinaryUuidCodec.toBytes(outboxId)
            );
            Instant replayedAt = Instant.parse("2026-07-07T00:03:00Z");
            JdbcOutboxGovernanceAdapter adapter = new JdbcOutboxGovernanceAdapter(
                    jdbcTemplate,
                    new JdbcOutboxEventStore(jdbcTemplate),
                    Clock.fixed(replayedAt, ZoneOffset.UTC)
            );

            var rows = adapter.findEvents(new FindOutboxEventsCommand(
                    OutboxEventStatus.DEAD,
                    "eventbus.content",
                    null,
                    createdAt.minusSeconds(1),
                    updatedAt.plusSeconds(1),
                    10
            ));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).id()).isEqualTo(outboxId);
            assertThat(rows.get(0).createdAt()).isEqualTo(createdAt);
            assertThat(rows.get(0).updatedAt()).isEqualTo(updatedAt);
            assertThat(adapter.findById(outboxId)).isPresent();
            assertThat(adapter.listBacklog()).extracting(row -> row.topic() + "|" + row.status() + "|" + row.count())
                    .contains("eventbus.content|DEAD|1", "projection.im.policy|PENDING|1");

            assertThat(adapter.requeueDead(outboxId, "operator replay")).isTrue();
            var replayed = adapter.findById(outboxId).orElseThrow();
            assertThat(replayed.status()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(replayed.retryCount()).isZero();
            assertThat(replayed.lastError()).isEqualTo("replay requested: operator replay");
            Timestamp nextRetryAt = jdbcTemplate.queryForObject(
                    "select next_retry_at from outbox_event where id = ?",
                    Timestamp.class,
                    BinaryUuidCodec.toBytes(outboxId)
            );
            assertThat(nextRetryAt).isNotNull();
            assertThat(nextRetryAt.toInstant()).isEqualTo(replayedAt);
            assertThat(jdbcTemplate.queryForObject(
                    "select lease_token from outbox_event where id = ?",
                    byte[].class,
                    BinaryUuidCodec.toBytes(outboxId)
            )).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "select processing_lease_until from outbox_event where id = ?",
                    Timestamp.class,
                    BinaryUuidCodec.toBytes(outboxId)
            )).isNull();
        } finally {
            db.shutdown();
        }
    }

    @Test
    void catalogShouldTrimTopicsAndIgnoreBlanks() {
        ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
        when(handlersProvider.getIfAvailable()).thenReturn(List.of(
                handler(" eventbus.content "),
                handler(""),
                handler(null),
                handler("projection.im.policy")
        ));

        SpringOutboxHandlerCatalog catalog = new SpringOutboxHandlerCatalog(handlersProvider);

        assertThat(catalog.hasHandler(" eventbus.content ")).isTrue();
        assertThat(catalog.hasHandler("projection.missing")).isFalse();
        assertThat(catalog.topics()).containsExactlyInAnyOrder(
                "eventbus.content",
                "projection.im.policy"
        );
    }

    private static OutboxHandler handler(String topic) {
        return new OutboxHandler() {
            @Override
            public String topic() {
                return topic;
            }

            @Override
            public void handle(OutboxEvent event) {
            }
        };
    }

    private static void insertEvent(
            JdbcTemplate jdbcTemplate,
            UUID id,
            String eventId,
            String topic,
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
                "post-1",
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
        jdbcTemplate.execute("delete from outbox_event");
    }
}
