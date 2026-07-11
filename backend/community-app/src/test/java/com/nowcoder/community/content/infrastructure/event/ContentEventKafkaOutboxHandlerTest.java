package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.application.ContentEventDispatchApplicationService;
import com.nowcoder.community.content.application.command.DispatchContentEventCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ContentEventKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.eventbus.content";

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
    void handleShouldPreserveStoredPayloadAndEventKeyForOutboxReplayDispatch() {
        ContentEventDispatchApplicationService applicationService = mock(ContentEventDispatchApplicationService.class);
        ContentEventKafkaOutboxHandler handler = new ContentEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);
        String payload = "{\"eventId\":\"content:PostPublished:post-1\",\"type\":\"PostPublished\"}";

        handler.handle(outboxEvent(payload, "post-1"));

        ArgumentCaptor<DispatchContentEventCommand> commandCaptor = ArgumentCaptor.forClass(DispatchContentEventCommand.class);
        verify(applicationService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue().eventKey()).isEqualTo("post-1");
        assertThat(commandCaptor.getValue().payloadJson()).isEqualTo(payload);
        assertThat(commandCaptor.getValue().payloadJson()).contains("content:PostPublished:post-1");
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
