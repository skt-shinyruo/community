package com.nowcoder.community.common.outbox;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.id.BinaryUuidCodec;
import com.nowcoder.community.common.logging.EventLogFields;
import com.nowcoder.community.common.trace.TraceContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OutboxWorkerRetryTest {

    private static final String TOPIC = "projection.points";
    private static final String PAYLOAD_SECRET = "payload-secret-must-not-be-logged";
    private static final String LAST_ERROR_SECRET = "last-error-secret-must-not-be-logged";
    private static final String EXCEPTION_MESSAGE_SECRET = "exception-message-secret-must-not-be-logged";

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
        OutboxLease lease = leaseFor(outboxId);
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
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(Optional.of(lease));
        when(store.markDead(lease, now, "java.lang.RuntimeException: boom")).thenReturn(true);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

            int processed = worker.pollOnce();

            assertThat(processed).isEqualTo(1);
            verify(store).markDead(same(lease), eq(now), eq("java.lang.RuntimeException: boom"));
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
        OutboxLease lease = leaseFor(outboxId);
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
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(Optional.of(lease));
        when(store.markFailedAndScheduleRetry(
                lease,
                now,
                now.plus(Duration.ofSeconds(10)),
                "no handler for topic=projection.points"
        )).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(store, Map.of(), properties, Clock.fixed(now, ZoneOffset.UTC));

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            int processed = worker.pollOnce();

            assertThat(processed).isEqualTo(1);
            verify(store).markFailedAndScheduleRetry(
                    same(lease),
                    eq(now),
                    eq(now.plus(Duration.ofSeconds(10))),
                    eq("no handler for topic=projection.points")
            );
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
        OutboxLease lease = leaseFor(outboxId);
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
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(Optional.of(lease));
        when(store.markSucceeded(lease, now)).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

        int processed = worker.pollOnce();

        assertThat(processed).isEqualTo(1);
        assertThat(seenTrace.get()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        assertThat(com.nowcoder.community.common.trace.TraceId.get()).isNull();
        verify(store).markSucceeded(same(lease), eq(now));
    }

    @Test
    void workerShouldUseOutboxTraceWhenJobSpanIsActive() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000004");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = new OutboxEvent(
                outboxId,
                "e-trace-active:points",
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
        SpanContext activeSpanContext = SpanContext.create(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "bbbbbbbbbbbbbbbb",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );

        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(java.util.List.of(event));
        when(store.tryClaimProcessing(eq(outboxId), any(), eq(now))).thenReturn(Optional.of(lease));
        when(store.markSucceeded(lease, now)).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(store, Map.of(handler.topic(), handler), properties, Clock.fixed(now, ZoneOffset.UTC));

        try (Scope ignored = Span.wrap(activeSpanContext).makeCurrent()) {
            int processed = worker.pollOnce();

            assertThat(processed).isEqualTo(1);
            assertThat(seenTrace.get()).isEqualTo("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
            assertThat(com.nowcoder.community.common.trace.TraceId.get()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        }
        verify(store).markSucceeded(same(lease), eq(now));
    }

    @Test
    void workerShouldUseClaimedLeaseWhenSchedulingHandlerExceptionRetry() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000005");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = leaseLossEvent(outboxId, "e-retry:points", 0);
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = failingHandler(handlerCalls);

        stubClaim(store, properties, now, event, lease);
        when(store.markFailedAndScheduleRetry(
                lease,
                now,
                now.plus(Duration.ofSeconds(1)),
                "java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET
        )).thenReturn(true);

        OutboxWorker worker = new OutboxWorker(
                store,
                Map.of(handler.topic(), handler),
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThat(worker.pollOnce()).isEqualTo(1);

        assertThat(handlerCalls).hasValue(1);
        verify(store).markFailedAndScheduleRetry(
                same(lease),
                eq(now),
                eq(now.plus(Duration.ofSeconds(1))),
                eq("java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET)
        );
    }

    @Test
    void workerShouldSignalLeaseLossWhenSuccessMarkLosesLease() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000006");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = leaseLossEvent(outboxId, "e-lease-lost-success", 2);
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = successfulHandler(handlerCalls);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        stubClaim(store, properties, now, event, lease);
        when(store.markSucceeded(lease, now)).thenReturn(false);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(handler.topic(), handler),
                    properties,
                    Clock.fixed(now, ZoneOffset.UTC),
                    registry
            );

            assertThat(worker.pollOnce()).isEqualTo(1);

            assertThat(handlerCalls).hasValue(1);
            verify(store).markSucceeded(same(lease), eq(now));
            verifyClaimInteractions(store, properties, now, event);
            verifyNoMoreInteractions(store);
            assertLeaseLostSignal(logs, registry, event, lease, TOPIC, "success", null, 2);
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void workerShouldSignalLeaseLossWhenNoHandlerRetryMarkLosesLease() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000007");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = leaseLossEvent(outboxId, "e-lease-lost-no-handler", 2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        stubClaim(store, properties, now, event, lease);
        when(store.markFailedAndScheduleRetry(
                lease,
                now,
                now.plus(Duration.ofSeconds(10)),
                "no handler for topic=" + TOPIC
        )).thenReturn(false);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(),
                    properties,
                    Clock.fixed(now, ZoneOffset.UTC),
                    registry
            );

            assertThat(worker.pollOnce()).isEqualTo(1);

            verify(store).markFailedAndScheduleRetry(
                    same(lease),
                    eq(now),
                    eq(now.plus(Duration.ofSeconds(10))),
                    eq("no handler for topic=" + TOPIC)
            );
            verifyClaimInteractions(store, properties, now, event);
            verifyNoMoreInteractions(store);
            assertLeaseLostSignal(logs, registry, event, lease, "unhandled", "retry", null, 3);
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void workerShouldSignalLeaseLossWhenHandlerExceptionRetryMarkLosesLease() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000008");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = leaseLossEvent(outboxId, "e-lease-lost-retry", 0);
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = failingHandler(handlerCalls);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        stubClaim(store, properties, now, event, lease);
        when(store.markFailedAndScheduleRetry(
                lease,
                now,
                now.plus(Duration.ofSeconds(1)),
                "java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET
        )).thenReturn(false);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(handler.topic(), handler),
                    properties,
                    Clock.fixed(now, ZoneOffset.UTC),
                    registry
            );

            assertThat(worker.pollOnce()).isEqualTo(1);

            assertThat(handlerCalls).hasValue(1);
            verify(store).markFailedAndScheduleRetry(
                    same(lease),
                    eq(now),
                    eq(now.plus(Duration.ofSeconds(1))),
                    eq("java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET)
            );
            verifyClaimInteractions(store, properties, now, event);
            verifyNoMoreInteractions(store);
            assertLeaseLostSignal(
                    logs,
                    registry,
                    event,
                    lease,
                    TOPIC,
                    "retry",
                    RuntimeException.class.getName(),
                    1
            );
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void workerShouldSignalLeaseLossWhenDeadMarkLosesLease() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxProperties properties = enabledProperties();
        properties.setMaxRetries(0);
        UUID outboxId = UUID.fromString("01965429-b34a-7000-8000-000000000009");
        OutboxLease lease = leaseFor(outboxId);
        OutboxEvent event = leaseLossEvent(outboxId, "e-lease-lost-dead", 0);
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = failingHandler(handlerCalls);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        stubClaim(store, properties, now, event, lease);
        when(store.markDead(
                lease,
                now,
                "java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET
        )).thenReturn(false);

        ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
        try {
            OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(handler.topic(), handler),
                    properties,
                    Clock.fixed(now, ZoneOffset.UTC),
                    registry
            );

            assertThat(worker.pollOnce()).isEqualTo(1);

            assertThat(handlerCalls).hasValue(1);
            verify(store).markDead(
                    same(lease),
                    eq(now),
                    eq("java.lang.RuntimeException: " + EXCEPTION_MESSAGE_SECRET)
            );
            verifyClaimInteractions(store, properties, now, event);
            verifyNoMoreInteractions(store);
            assertLeaseLostSignal(
                    logs,
                    registry,
                    event,
                    lease,
                    TOPIC,
                    "dead",
                    RuntimeException.class.getName(),
                    1
            );
        } finally {
            stopOutboxWorkerLogCapture(logs);
        }
    }

    @Test
    void staleWorkerShouldOnlySignalLeaseLossAfterExpiredLeaseIsReclaimed() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(db);
            createOutboxSchema(jdbcTemplate);
            JdbcOutboxEventStore store = new JdbcOutboxEventStore(jdbcTemplate);
            Instant now = Instant.parse("2026-03-14T00:00:00Z");
            Instant recoveryTime = now.plusSeconds(2);
            Instant leaseBDeadline = now.plusSeconds(60);
            OutboxProperties properties = enabledProperties();
            properties.setProcessingLease(Duration.ofSeconds(1));

            assertThat(store.enqueue("e-reclaimed", TOPIC, "1", PAYLOAD_SECRET)).isTrue();
            jdbcTemplate.update(
                    "update outbox_event set retry_count = ?, last_error = ? where event_id = ?",
                    2,
                    LAST_ERROR_SECRET,
                    "e-reclaimed"
            );

            AtomicInteger handlerCalls = new AtomicInteger();
            AtomicReference<UUID> leaseAToken = new AtomicReference<>();
            AtomicReference<OutboxLease> leaseB = new AtomicReference<>();
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            OutboxHandler handler = new OutboxHandler() {
                @Override
                public String topic() {
                    return TOPIC;
                }

                @Override
                public void handle(OutboxEvent event) {
                    handlerCalls.incrementAndGet();
                    byte[] tokenBytes = jdbcTemplate.queryForObject(
                            "select lease_token from outbox_event where event_id = ?",
                            byte[].class,
                            event.eventId()
                    );
                    leaseAToken.set(BinaryUuidCodec.fromBytes(tokenBytes));
                    assertThat(store.recoverExpiredLeases(recoveryTime)).isEqualTo(1);
                    assertThat(store.findDuePending(10, recoveryTime)).hasSize(1);
                    leaseB.set(store.tryClaimProcessing(event.id(), leaseBDeadline, recoveryTime).orElseThrow());
                }
            };
            OutboxWorker worker = new OutboxWorker(
                    store,
                    Map.of(handler.topic(), handler),
                    properties,
                    Clock.fixed(now, ZoneOffset.UTC),
                    registry
            );

            ListAppender<ILoggingEvent> logs = startOutboxWorkerLogCapture();
            try {
                assertThat(worker.pollOnce()).isEqualTo(1);

                assertThat(handlerCalls).hasValue(1);
                assertThat(leaseB.get()).isNotNull();
                assertThat(leaseB.get().token()).isNotEqualTo(leaseAToken.get());
                assertThat(jdbcTemplate.queryForObject(
                        "select status from outbox_event where event_id = ?",
                        String.class,
                        "e-reclaimed"
                )).isEqualTo(OutboxEventStatus.PROCESSING);
                assertThat(BinaryUuidCodec.fromBytes(jdbcTemplate.queryForObject(
                        "select lease_token from outbox_event where event_id = ?",
                        byte[].class,
                        "e-reclaimed"
                ))).isEqualTo(leaseB.get().token());
                assertThat(jdbcTemplate.queryForObject(
                        "select processing_lease_until from outbox_event where event_id = ?",
                        Timestamp.class,
                        "e-reclaimed"
                ).toInstant()).isEqualTo(leaseBDeadline);
                assertThat(jdbcTemplate.queryForObject(
                        "select retry_count from outbox_event where event_id = ?",
                        Integer.class,
                        "e-reclaimed"
                )).isEqualTo(2);
                assertThat(jdbcTemplate.queryForObject(
                        "select last_error from outbox_event where event_id = ?",
                        String.class,
                        "e-reclaimed"
                )).isEqualTo(LAST_ERROR_SECRET);

                OutboxEvent event = leaseLossEvent(leaseB.get().rowId(), "e-reclaimed", 2);
                assertLeaseLostSignal(
                        logs,
                        registry,
                        event,
                        new OutboxLease(event.id(), leaseAToken.get()),
                        TOPIC,
                        "success",
                        null,
                        2
                );
            } finally {
                stopOutboxWorkerLogCapture(logs);
            }
        } finally {
            db.shutdown();
        }
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

    private static OutboxLease leaseFor(UUID rowId) {
        return new OutboxLease(
                rowId,
                UUID.fromString("01965429-b34a-7000-8000-ffffffffffff")
        );
    }

    private static OutboxEvent leaseLossEvent(UUID rowId, String eventId, int retryCount) {
        return new OutboxEvent(
                rowId,
                eventId,
                TOPIC,
                "1",
                PAYLOAD_SECRET,
                OutboxEventStatus.PENDING,
                retryCount,
                null,
                LAST_ERROR_SECRET,
                null,
                null
        );
    }

    private static OutboxHandler successfulHandler(AtomicInteger handlerCalls) {
        return new OutboxHandler() {
            @Override
            public String topic() {
                return TOPIC;
            }

            @Override
            public void handle(OutboxEvent event) {
                handlerCalls.incrementAndGet();
            }
        };
    }

    private static OutboxHandler failingHandler(AtomicInteger handlerCalls) {
        return new OutboxHandler() {
            @Override
            public String topic() {
                return TOPIC;
            }

            @Override
            public void handle(OutboxEvent event) {
                handlerCalls.incrementAndGet();
                throw new RuntimeException(EXCEPTION_MESSAGE_SECRET);
            }
        };
    }

    private static void stubClaim(
            JdbcOutboxEventStore store,
            OutboxProperties properties,
            Instant now,
            OutboxEvent event,
            OutboxLease lease
    ) {
        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of(event));
        when(store.tryClaimProcessing(event.id(), now.plus(properties.getProcessingLease()), now))
                .thenReturn(Optional.of(lease));
    }

    private static void verifyClaimInteractions(
            JdbcOutboxEventStore store,
            OutboxProperties properties,
            Instant now,
            OutboxEvent event
    ) {
        verify(store).recoverExpiredLeases(now);
        verify(store).findDuePending(properties.getBatchSize(), now);
        verify(store).tryClaimProcessing(event.id(), now.plus(properties.getProcessingLease()), now);
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

    private void assertLeaseLostSignal(
            ListAppender<ILoggingEvent> logs,
            SimpleMeterRegistry registry,
            OutboxEvent event,
            OutboxLease lease,
            String metricTopic,
            String transition,
            String errorClass,
            int retryCount
    ) {
        assertThat(logs.list).hasSize(1);
        ILoggingEvent warning = findSingleEventByActionAndOutcome(logs, "outbox_lease_lost", "degraded");
        assertThat(warning.getMDCPropertyMap())
                .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                .containsEntry(EventLogFields.EVENT_ACTION, "outbox_lease_lost")
                .containsEntry(EventLogFields.EVENT_OUTCOME, "degraded");
        assertThat(warning.getFormattedMessage())
                .contains("community.event_id=" + event.eventId())
                .contains("community.topic=" + event.topic())
                .contains("community.transition=" + transition)
                .contains("community.retry_count=" + retryCount)
                .doesNotContain(lease.token().toString())
                .doesNotContain(lease.toString())
                .doesNotContain(PAYLOAD_SECRET)
                .doesNotContain(LAST_ERROR_SECRET)
                .doesNotContain(EXCEPTION_MESSAGE_SECRET);
        if (errorClass == null) {
            assertThat(warning.getFormattedMessage()).doesNotContain("community.error_class=");
        } else {
            assertThat(warning.getFormattedMessage()).contains("community.error_class=" + errorClass);
        }
        assertThat(warning.getThrowableProxy()).isNull();

        Counter counter = registry.get("outbox.lease.lost")
                .tags("topic", metricTopic, "transition", transition)
                .counter();
        assertThat(counter.count()).isEqualTo(1.0d);
        assertThat(counter.getId().getTags())
                .extracting(Tag::getKey, Tag::getValue)
                .containsExactly(
                        tuple("topic", metricTopic),
                        tuple("transition", transition)
                );
        assertThat(registry.getMeters()).containsExactly(counter);
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
