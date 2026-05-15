package com.nowcoder.community.common.outbox;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OutboxWorkerRetryTest {

    @Test
    void workerShouldRetryFailedHandlerAndEventuallySucceed(CapturedOutput output) {
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
            ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
            try {
                worker.pollOnce();
                assertThat(attempts.get()).isEqualTo(1);
                ILoggingEvent retryEvent = findSingleEventByActionAndOutcome(logs, "outbox_dispatch", "retry");
                assertThat(retryEvent.getMDCPropertyMap())
                        .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                        .containsEntry(EventLogFields.EVENT_ACTION, "outbox_dispatch")
                        .containsEntry(EventLogFields.EVENT_OUTCOME, "retry");
                assertThat(retryEvent.getFormattedMessage())
                        .contains("community.event_id=e-2:points")
                        .contains("community.topic=projection.points")
                        .contains("community.retry_count=1")
                        .contains("community.error_class=java.lang.RuntimeException")
                        .contains("community.error_message=boom");
                assertThat(output.getAll()).doesNotContain("\tat ");
            } finally {
                stopOutboxWorkerLogCapture(logs);
            }

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

    @Test
    void workerShouldLogDeadWhenRetryLimitExceeded(CapturedOutput output) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        properties.setMaxRetries(0);
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000001");
        OutboxEvent event = new OutboxEvent(
                outboxId,
                "e-dead:points",
                "projection.points",
                "1",
                "{}",
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa-00f067aa0ba902b7-01"
        );
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public String topic() {
                return "projection.points";
            }

            @Override
            public void handle(OutboxEvent ignored) {
                throw new RuntimeException("boom");
            }
        };

        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(java.util.List.of(event));
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(true);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

            int processed = worker.pollOnce();

            assertThat(processed).isEqualTo(1);
            verify(store).markDead(outboxId, now, "java.lang.RuntimeException: boom");
            ILoggingEvent deadEvent = findSingleEventByActionAndOutcome(logs, "outbox_dispatch", "dead");
            assertThat(deadEvent.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "outbox_dispatch")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "dead")
                    .containsEntry(TraceContext.MDC_KEY_TRACE_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            assertThat(deadEvent.getFormattedMessage())
                    .contains("community.event_id=e-dead:points")
                    .contains("community.topic=projection.points")
                    .contains("community.retry_count=1")
                    .contains("community.error_class=java.lang.RuntimeException")
                    .contains("community.error_message=boom");
            assertThat(output.getAll()).doesNotContain("\tat ");
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void workerShouldLogDegradedWhenNoHandlerIsRegistered(CapturedOutput output) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000002");
        OutboxEvent event = new OutboxEvent(
                outboxId,
                "e-missing:points",
                "projection.points",
                "1",
                "{}",
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                null,
                "00-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb-00f067aa0ba902b7-01"
        );

        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(java.util.List.of(event));
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(store, Map.of(), properties, Clock.fixed(now, ZoneOffset.UTC));

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            int processed = worker.pollOnce();

            assertThat(processed).isEqualTo(1);
            verify(store).markFailedAndScheduleRetry(outboxId, now, now.plus(Duration.ofSeconds(10)), "no handler for topic=projection.points");
            ILoggingEvent missingHandlerEvent = findSingleEventByActionAndOutcome(logs, "outbox_dispatch", "degraded");
            assertThat(missingHandlerEvent.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "outbox_dispatch")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "degraded")
                    .containsEntry(TraceContext.MDC_KEY_TRACE_ID, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
            assertThat(missingHandlerEvent.getFormattedMessage())
                    .contains("community.reason_code=no_handler")
                    .contains("community.event_id=e-missing:points")
                    .contains("community.topic=projection.points");
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void workerShouldRestoreOutboxTraceDuringHandlerAndClearAfterwards() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000003");
        OutboxEvent event = new OutboxEvent(
                outboxId,
                "e-trace:points",
                "projection.points",
                "1",
                "{}",
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                "00-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee-00f067aa0ba902b7-01"
        );
        AtomicReference<String> seenTrace = new AtomicReference<>();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public String topic() {
                return "projection.points";
            }

            @Override
            public void handle(OutboxEvent ignored) {
                seenTrace.set(com.nowcoder.community.common.trace.TraceId.get());
            }
        };

        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(java.util.List.of(event));
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

        int processed = worker.pollOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(seenTrace.get()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        assertThat(com.nowcoder.community.common.trace.TraceId.get()).isNull();
        verify(store).markSucceeded(outboxId, now);
    }

    private static OutboxProperties enabledProperties() {
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setBatchSize(10);
        properties.setProcessingLease(Duration.ofSeconds(30));
        properties.setBaseBackoff(Duration.ofSeconds(1));
        properties.setMaxBackoff(Duration.ofSeconds(60));
        properties.setMaxRetries(3);
        return properties;
    }

    private ListAppender<ILoggingEvent> startOutboxWorkerLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OutboxWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopOutboxWorkerLogCapture(ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OutboxWorker.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent findSingleEventByActionAndOutcome(ListAppender<ILoggingEvent> appender, String action, String outcome) {
        return appender.list.stream()
                .filter(event -> event != null
                        && action.equals(event.getMDCPropertyMap().get(EventLogFields.EVENT_ACTION))
                        && outcome.equals(event.getMDCPropertyMap().get(EventLogFields.EVENT_OUTCOME)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log event found with action=" + action + " outcome=" + outcome));
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
