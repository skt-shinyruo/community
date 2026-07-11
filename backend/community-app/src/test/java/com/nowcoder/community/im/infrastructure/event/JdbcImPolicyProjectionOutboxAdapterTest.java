package com.nowcoder.community.im.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.im.application.ImPolicyProjectionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.List;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;

class JdbcImPolicyProjectionOutboxAdapterTest {

    @Test
    void duplicateSourceEventShouldCreateOneDeterministicOutboxRow() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            createSchema(jdbcTemplate);
            JdbcImPolicyProjectionOutboxAdapter adapter = new JdbcImPolicyProjectionOutboxAdapter(
                    new JdbcOutboxEventStore(jdbcTemplate),
                    new JacksonJsonCodec(JsonMappers.standard()),
                    "projection.im.policy"
            );
            ImPolicyProjectionEvent event = new ImPolicyProjectionEvent(
                    "user", "source-1", "USER_POLICY", uuid(7), null, null,
                    true, false, true, 1712345678901L, null, false,
                    1712345678901L, 777L
            );

            adapter.enqueue(event);
            adapter.enqueue(event);

            List<String> eventIds = jdbcTemplate.queryForList(
                    "select event_id from outbox_event", String.class);
            assertThat(eventIds).hasSize(1);
            assertThat(eventIds.get(0)).startsWith("ip:u:").hasSize(48).matches("[\\x00-\\x7F]+");
            String payload = jdbcTemplate.queryForObject(
                    "select payload from outbox_event where event_id = ?", String.class, eventIds.get(0));
            JsonNode json = new ObjectMapper().readTree(payload);
            assertThat(json.path("sourceEventId").asText()).isEqualTo("source-1");
            assertThat(json.path("version").asLong()).isEqualTo(777L);
        } finally {
            database.shutdown();
        }
    }

    @Test
    void blockRelationShouldUseSocialDeterministicOutboxPrefix() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            createSchema(jdbcTemplate);
            JdbcImPolicyProjectionOutboxAdapter adapter = new JdbcImPolicyProjectionOutboxAdapter(
                    new JdbcOutboxEventStore(jdbcTemplate),
                    new JacksonJsonCodec(JsonMappers.standard()),
                    "projection.im.policy"
            );
            ImPolicyProjectionEvent event = new ImPolicyProjectionEvent(
                    "social", "source-block-1", "BLOCK", uuid(11), uuid(22), true,
                    false, false, false, null, null, false,
                    1712345678901L, 888L
            );

            adapter.enqueue(event);
            adapter.enqueue(event);

            List<String> eventIds = jdbcTemplate.queryForList(
                    "select event_id from outbox_event", String.class);
            assertThat(eventIds).hasSize(1);
            assertThat(eventIds.get(0))
                    .startsWith("ip:s:")
                    .hasSize(48)
                    .matches("[\\x00-\\x7F]+");
            String payload = jdbcTemplate.queryForObject(
                    "select payload from outbox_event where event_id = ?", String.class, eventIds.get(0));
            JsonNode json = new ObjectMapper().readTree(payload);
            assertThat(json.path("kind").asText()).isEqualTo("BLOCK");
            assertThat(json.path("sourceEventId").asText()).isEqualTo("source-block-1");
        } finally {
            database.shutdown();
        }
    }

    private static void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (" +
                        "id binary(16) primary key, event_id varchar(64) not null, " +
                        "topic varchar(255) not null, event_key varchar(255) not null, " +
                        "payload clob not null, status varchar(32) not null, retry_count int not null default 0, " +
                        "next_retry_at timestamp, last_error varchar(512), trace_id varchar(32), " +
                        "traceparent varchar(128), created_at timestamp default current_timestamp, " +
                        "updated_at timestamp default current_timestamp, constraint uk_outbox_event_id unique (event_id))"
        );
    }
}
