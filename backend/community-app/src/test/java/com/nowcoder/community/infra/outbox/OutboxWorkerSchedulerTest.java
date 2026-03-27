package com.nowcoder.community.infra.outbox;

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

        scheduler.poll();

        assertThat(output.getAll())
                .contains("community.category=async")
                .contains("community.action=outbox_poll")
                .contains("community.outcome=failure")
                .contains("community.reason_code=poll_failed");
    }
}
