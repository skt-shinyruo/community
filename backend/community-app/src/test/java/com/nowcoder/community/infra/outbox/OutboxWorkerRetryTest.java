package com.nowcoder.community.infra.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxWorkerRetryTest {

    @Test
    void workerShouldRetryFailedHandlerAndEventuallySucceed() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);

            Instant t0 = Instant.parse("2026-03-14T00:00:00Z");
            Clock clock = Clock.fixed(t0, ZoneOffset.UTC);

            OutboxProperties properties = new OutboxProperties();
            properties.setEnabled(true);
            properties.setBatchSize(10);
            properties.setProcessingLease(Duration.ofSeconds(30));
            properties.setBaseBackoff(Duration.ofSeconds(1));
            properties.setMaxBackoff(Duration.ofSeconds(60));
            properties.setMaxRetries(3);

            boolean inserted = store.enqueue(
                    "e-2:points",
                    "projection.points",
                    "1",
                    "{\"userId\":1,\"eventId\":\"e-2\",\"eventType\":\"LikeCreated\",\"delta\":1}"
            );
            assertThat(inserted).isTrue();

            AtomicInteger attempts = new AtomicInteger();
            OutboxHandler handler = new OutboxHandler() {
                @Override
                public String topic() {
                    return "projection.points";
                }

                @Override
                public void handle(OutboxEvent event) {
                    if (attempts.incrementAndGet() == 1) {
                        throw new RuntimeException("boom");
                    }
                }
            };

            OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, clock);

            worker.pollOnce();
            assertThat(attempts.get()).isEqualTo(1);

            String statusAfterFail = jdbcTemplate.queryForObject(
                    "select status from outbox_event where event_id = ?",
                    String.class,
                    "e-2:points"
            );
            assertThat(statusAfterFail).isEqualTo(OutboxEventStatus.PENDING);

            Integer retryCount = jdbcTemplate.queryForObject(
                    "select retry_count from outbox_event where event_id = ?",
                    Integer.class,
                    "e-2:points"
            );
            assertThat(retryCount).isEqualTo(1);

            Timestamp nextRetryAt = jdbcTemplate.queryForObject(
                    "select next_retry_at from outbox_event where event_id = ?",
                    Timestamp.class,
                    "e-2:points"
            );
            assertThat(nextRetryAt).isNotNull();
            assertThat(nextRetryAt.toInstant()).isAfter(t0);

            // make it due now to simulate time passing
            jdbcTemplate.update(
                    "update outbox_event set next_retry_at = ? where event_id = ?",
                    Timestamp.from(t0),
                    "e-2:points"
            );

            worker.pollOnce();
            assertThat(attempts.get()).isEqualTo(2);

            String statusAfterSuccess = jdbcTemplate.queryForObject(
                    "select status from outbox_event where event_id = ?",
                    String.class,
                    "e-2:points"
            );
            assertThat(statusAfterSuccess).isEqualTo(OutboxEventStatus.SUCCEEDED);
        } finally {
            db.shutdown();
        }
    }

    private static void createOutboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute(
                "create table if not exists outbox_event (\n" +
                        "  id bigint auto_increment primary key,\n" +
                        "  event_id varchar(64) not null,\n" +
                        "  topic varchar(255) not null,\n" +
                        "  event_key varchar(255) not null,\n" +
                        "  payload clob not null,\n" +
                        "  status varchar(32) not null,\n" +
                        "  retry_count int not null default 0,\n" +
                        "  next_retry_at timestamp,\n" +
                        "  last_error varchar(512),\n" +
                        "  created_at timestamp default current_timestamp,\n" +
                        "  updated_at timestamp default current_timestamp,\n" +
                        "  constraint uk_outbox_event_id unique (event_id)\n" +
                        ")"
        );
        jdbcTemplate.execute("create index if not exists idx_outbox_status_next on outbox_event(status, next_retry_at, id)");
        jdbcTemplate.execute("delete from outbox_event");
    }
}
