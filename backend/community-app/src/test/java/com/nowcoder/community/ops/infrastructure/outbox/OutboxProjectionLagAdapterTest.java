package com.nowcoder.community.ops.infrastructure.outbox;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.outbox.OutboxHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxProjectionLagAdapterTest {

    @Test
    void listProjectionLagShouldUseRegisteredProjectionTopicsOnly() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            insertEvent(jdbcTemplate, "projection.im.policy", OutboxEventStatus.PENDING, Instant.parse("2026-07-07T00:00:00Z"));
            insertEvent(jdbcTemplate, "eventbus.content", OutboxEventStatus.PENDING, Instant.parse("2026-07-07T00:00:00Z"));

            ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
            when(handlersProvider.getIfAvailable()).thenReturn(List.of(
                    handler("projection.im.policy"),
                    handler("eventbus.content")
            ));

            OutboxProjectionLagAdapter adapter = new OutboxProjectionLagAdapter(
                    jdbcTemplate,
                    new SpringOutboxHandlerCatalog(handlersProvider)
            );

            var rows = adapter.listProjectionLag();

            assertThat(rows).extracting(row -> row.projection() + "|" + row.status() + "|" + row.count())
                    .containsExactly("projection.im.policy|PENDING|1");
        } finally {
            db.shutdown();
        }
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

    private static void insertEvent(JdbcTemplate jdbcTemplate, String topic, String status, Instant createdAt) {
        jdbcTemplate.update(
                """
                        insert into outbox_event(
                          id, event_id, topic, event_key, payload, status, retry_count,
                          next_retry_at, last_error, trace_id, traceparent, created_at, updated_at
                        ) values (random_uuid(), ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?)
                        """,
                "event-" + topic,
                topic,
                "key-" + topic,
                "{\"topic\":\"" + topic + "\"}",
                status,
                0,
                null,
                null,
                null,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt.plusSeconds(1))
        );
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (\n" +
                        "  id uuid primary key,\n" +
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
        jdbcTemplate.execute("delete from outbox_event");
    }
}
