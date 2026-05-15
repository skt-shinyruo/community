package com.nowcoder.community.common.outbox;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nowcoder.community.common.logging.EventLogFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
