package com.nowcoder.community.analytics.infrastructure.event;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsRequestKafkaListenerTest {

    @Test
    void asyncPublisherShouldRequireAnalyticsIngestAndAsyncCaptureToBeEnabled() {
        ConditionalOnProperty conditional = AnalyticsRequestEventPublisher.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional.prefix()).isEqualTo("analytics.ingest");
        assertThat(conditional.name()).containsExactly("enabled", "async-enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }

    @Test
    void kafkaListenerShouldRequireAnalyticsIngestAndAsyncCaptureToBeEnabled() {
        ConditionalOnProperty conditional = AnalyticsRequestKafkaListener.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional.prefix()).isEqualTo("analytics.ingest");
        assertThat(conditional.name()).containsExactly("enabled", "async-enabled");
        assertThat(conditional.havingValue()).isEqualTo("true");
    }

    @Test
    void kafkaListenerShouldTranslateEventIntoRecordRequestCommand() {
        AnalyticsIngestApplicationService analyticsIngestApplicationService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestKafkaListener listener = new AnalyticsRequestKafkaListener(analyticsIngestApplicationService);
        AnalyticsRequestEvent event = new AnalyticsRequestEvent("127.0.0.1", UUID.randomUUID(), true, true);

        listener.onMessage(event);

        verify(analyticsIngestApplicationService).recordRequest(new RecordRequestCommand(
                event.ip(),
                event.userId(),
                event.recordUv(),
                event.recordDau()
        ));
    }

    @Test
    void kafkaListenerShouldIgnoreNullEvent() {
        AnalyticsIngestApplicationService analyticsIngestApplicationService = mock(AnalyticsIngestApplicationService.class);
        AnalyticsRequestKafkaListener listener = new AnalyticsRequestKafkaListener(analyticsIngestApplicationService);

        listener.onMessage(null);

        verifyNoInteractions(analyticsIngestApplicationService);
    }
}
