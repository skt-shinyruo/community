package com.nowcoder.community.common.outbox;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.logging.EventLogFields;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OutboxWorkerSchedulerTest {

    @Test
    void pollShouldLogAsyncFailureWhenWorkerThrows(CapturedOutput output) {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);

        when(handlersProvider.getIfAvailable()).thenReturn(List.of());
        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenThrow(new RuntimeException("boom"));

        OutboxWorkerScheduler scheduler = new OutboxWorkerScheduler(
                store,
                handlersProvider,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        ListAppender<ILoggingEvent> logs = startOutboxWorkerSchedulerLogCapture();
        try {
            scheduler.poll();

            ILoggingEvent event = logs.list.stream()
                    .filter(candidate -> "outbox_poll".equals(candidate.getMDCPropertyMap().get(EventLogFields.EVENT_ACTION)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No outbox_poll event found"));
            assertThat(event.getMDCPropertyMap())
                    .containsEntry(EventLogFields.EVENT_CATEGORY, "async")
                    .containsEntry(EventLogFields.EVENT_ACTION, "outbox_poll")
                    .containsEntry(EventLogFields.EVENT_OUTCOME, "failure");
            assertThat(event.getFormattedMessage()).contains("community.reason_code=poll_failed");
        } finally {
            stopOutboxWorkerSchedulerLogCapture(logs);
        }
        assertThat(output.getAll()).doesNotContain("\tat ");
    }

    @Test
    void schedulerShouldPassOptionalMeterRegistryToWorker() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);
        properties.setProcessingLease(Duration.ofSeconds(30));
        UUID rowId = UUID.fromString("01965429-b34a-7000-8000-000000000101");
        OutboxLease lease = new OutboxLease(
                rowId,
                UUID.fromString("01965429-b34a-7000-8000-000000000102")
        );
        OutboxEvent event = new OutboxEvent(
                rowId,
                "e-scheduler-lease-lost",
                "projection.points",
                "1",
                "{}",
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                null,
                null
        );
        AtomicInteger handlerCalls = new AtomicInteger();
        OutboxHandler handler = new OutboxHandler() {
            @Override
            public String topic() {
                return "projection.points";
            }

            @Override
            public void handle(OutboxEvent ignored) {
                handlerCalls.incrementAndGet();
            }
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        when(handlersProvider.getIfAvailable()).thenReturn(List.of(handler));
        when(meterRegistryProvider.getIfAvailable()).thenReturn(registry);
        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of(event));
        when(store.tryClaimProcessing(eq(rowId), any(), eq(now))).thenReturn(Optional.of(lease));
        when(store.markSucceeded(lease, now)).thenReturn(false);

        OutboxWorkerScheduler scheduler = new OutboxWorkerScheduler(
                store,
                handlersProvider,
                properties,
                Clock.fixed(now, ZoneOffset.UTC),
                meterRegistryProvider
        );

        scheduler.poll();

        assertThat(handlerCalls).hasValue(1);
        assertThat(registry.get("outbox.lease.lost")
                .tags("topic", "projection.points", "transition", "success")
                .counter()
                .count()).isEqualTo(1.0d);
        verify(meterRegistryProvider).getIfAvailable();
    }

    @Test
    void schedulerShouldWorkWhenOptionalMeterRegistryIsAbsent() {
        Instant now = Instant.parse("2026-03-14T00:00:00Z");
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<List<OutboxHandler>> handlersProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);
        OutboxProperties properties = new OutboxProperties();
        properties.setEnabled(true);

        when(handlersProvider.getIfAvailable()).thenReturn(List.of());
        when(meterRegistryProvider.getIfAvailable()).thenReturn(null);
        when(store.recoverExpiredLeases(now)).thenReturn(0);
        when(store.findDuePending(properties.getBatchSize(), now)).thenReturn(List.of());

        OutboxWorkerScheduler scheduler = new OutboxWorkerScheduler(
                store,
                handlersProvider,
                properties,
                Clock.fixed(now, ZoneOffset.UTC),
                meterRegistryProvider
        );

        assertThatCode(scheduler::poll).doesNotThrowAnyException();
        verify(meterRegistryProvider).getIfAvailable();
    }

    private ListAppender<ILoggingEvent> startOutboxWorkerSchedulerLogCapture() {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OutboxWorkerScheduler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void stopOutboxWorkerSchedulerLogCapture(ListAppender<ILoggingEvent> appender) {
        if (appender == null) {
            return;
        }
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(OutboxWorkerScheduler.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
