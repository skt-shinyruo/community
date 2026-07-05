package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.application.ContentEventDispatchApplicationService;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ContentEventKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.eventbus.content";

    @Test
    void handlerShouldOnlyLoadForContentOutboxKafkaPublisher() {
        ConditionalOnExpression conditional = ContentEventKafkaOutboxHandler.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void topicShouldReturnOutboxTopic() {
        ContentEventKafkaOutboxHandler handler = new ContentEventKafkaOutboxHandler(
                mock(ContentEventDispatchApplicationService.class),
                OUTBOX_TOPIC
        );

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateDispatchCommandToApplicationService() {
        ContentEventDispatchApplicationService applicationService = mock(ContentEventDispatchApplicationService.class);
        ContentEventKafkaOutboxHandler handler = new ContentEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(outboxEvent("{\"eventId\":\"event-id\"}", "key-1"));

        verify(applicationService).dispatch(new DispatchContentEventCommand("key-1", "{\"eventId\":\"event-id\"}"));
    }

    @Test
    void handleShouldIgnoreNullEvent() {
        ContentEventDispatchApplicationService applicationService = mock(ContentEventDispatchApplicationService.class);
        ContentEventKafkaOutboxHandler handler = new ContentEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(null);

        verifyNoInteractions(applicationService);
    }

    private static OutboxEvent outboxEvent(String payload, String key) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "outbox-row-event-id",
                OUTBOX_TOPIC,
                key,
                payload,
                "PENDING",
                0,
                null,
                null,
                null,
                null
        );
    }
}
