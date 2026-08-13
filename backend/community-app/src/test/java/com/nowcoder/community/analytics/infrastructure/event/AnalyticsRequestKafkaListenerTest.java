package com.nowcoder.community.analytics.infrastructure.event;

import com.nowcoder.community.analytics.application.AnalyticsIngestApplicationService;
import com.nowcoder.community.analytics.application.command.RecordRequestCommand;
import com.nowcoder.community.analytics.infrastructure.event.AnalyticsRequestKafkaListener.AnalyticsRequestEvent;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AnalyticsRequestKafkaListenerTest {

    @Test
    void publisherShouldMapCommandToKafkaEvent() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate = mock(KafkaTemplate.class);
        AnalyticsRequestEventPublisher publisher = new AnalyticsRequestEventPublisher(kafkaTemplate, "custom.analytics");
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        RecordRequestCommand command = new RecordRequestCommand("127.0.0.1", userId, true, false);

        publisher.publish(command);

        verify(kafkaTemplate).send(
                "custom.analytics",
                userId.toString(),
                new AnalyticsRequestEvent("127.0.0.1", userId, true, false)
        );
    }

    @Test
    void publisherShouldUseStableKeyForAnonymousRequest() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, AnalyticsRequestEvent> kafkaTemplate = mock(KafkaTemplate.class);
        AnalyticsRequestEventPublisher publisher = new AnalyticsRequestEventPublisher(kafkaTemplate, "analytics.request");

        publisher.publish(new RecordRequestCommand("127.0.0.1", null, true, false));

        verify(kafkaTemplate).send(
                "analytics.request",
                "anonymous",
                new AnalyticsRequestEvent("127.0.0.1", null, true, false)
        );
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
