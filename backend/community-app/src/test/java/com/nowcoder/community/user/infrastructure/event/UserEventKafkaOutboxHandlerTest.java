package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.user.application.UserEventDispatchApplicationService;
import com.nowcoder.community.user.application.command.DispatchUserEventCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class UserEventKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.eventbus.user";

    @Test
    void topicShouldReturnOutboxTopic() {
        UserEventKafkaOutboxHandler handler = new UserEventKafkaOutboxHandler(
                mock(UserEventDispatchApplicationService.class),
                OUTBOX_TOPIC
        );

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateDispatchCommandToApplicationService() {
        UserEventDispatchApplicationService applicationService = mock(UserEventDispatchApplicationService.class);
        UserEventKafkaOutboxHandler handler = new UserEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(outboxEvent("{\"eventId\":\"event-id\"}", "key-1"));

        verify(applicationService).dispatch(new DispatchUserEventCommand("key-1", "{\"eventId\":\"event-id\"}"));
    }

    @Test
    void handleShouldIgnoreNullEventOnly() {
        UserEventDispatchApplicationService applicationService = mock(UserEventDispatchApplicationService.class);
        UserEventKafkaOutboxHandler handler = new UserEventKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(null);
        handler.handle(outboxEvent(" ", "key-blank"));

        verify(applicationService).dispatch(new DispatchUserEventCommand("key-blank", " "));
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
