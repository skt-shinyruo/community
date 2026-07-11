package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.social.application.SocialEventDispatchApplicationService;
import com.nowcoder.community.social.application.command.DispatchSocialEventCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SocialEventKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.eventbus.social";

    @Test
    void topicShouldReturnOutboxTopic() {
        SocialEventKafkaOutboxHandler handler = new SocialEventKafkaOutboxHandler(
                mock(SocialEventDispatchApplicationService.class),
                OUTBOX_TOPIC
        );

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateDispatchCommandToApplicationService() {
        SocialEventDispatchApplicationService applicationService = mock(SocialEventDispatchApplicationService.class);
        SocialEventKafkaOutboxHandler handler = new SocialEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(outboxEvent("{\"eventId\":\"event-id\"}", "key-1"));

        verify(applicationService).dispatch(new DispatchSocialEventCommand("key-1", "{\"eventId\":\"event-id\"}"));
    }

    @Test
    void handleShouldPreserveStoredPayloadAndEventKeyForOutboxReplayDispatch() {
        SocialEventDispatchApplicationService applicationService = mock(SocialEventDispatchApplicationService.class);
        SocialEventKafkaOutboxHandler handler = new SocialEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);
        String payload = "{\"eventId\":\"se:like:created:source-1\",\"type\":\"LikeCreated\"}";

        handler.handle(outboxEvent(payload, "3:post-1"));

        ArgumentCaptor<DispatchSocialEventCommand> commandCaptor = ArgumentCaptor.forClass(DispatchSocialEventCommand.class);
        verify(applicationService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue().eventKey()).isEqualTo("3:post-1");
        assertThat(commandCaptor.getValue().payloadJson()).isEqualTo(payload);
        assertThat(commandCaptor.getValue().payloadJson()).contains("se:like:created:source-1");
    }

    @Test
    void handleShouldIgnoreNullEventOnly() {
        SocialEventDispatchApplicationService applicationService = mock(SocialEventDispatchApplicationService.class);
        SocialEventKafkaOutboxHandler handler = new SocialEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(null);
        handler.handle(outboxEvent(" ", "key-blank"));

        verify(applicationService).dispatch(new DispatchSocialEventCommand("key-blank", " "));
        verifyNoMoreInteractions(applicationService);
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
