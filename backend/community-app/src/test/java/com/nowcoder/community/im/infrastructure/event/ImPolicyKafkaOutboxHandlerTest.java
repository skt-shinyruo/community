package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.im.application.ImPolicyEventDispatchApplicationService;
import com.nowcoder.community.im.application.command.DispatchImPolicyEventCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ImPolicyKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.projection.im.policy";

    @Test
    void handlerShouldOnlyExposeApplicationServiceConstructor() {
        assertThat(ImPolicyKafkaOutboxHandler.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        ImPolicyEventDispatchApplicationService.class,
                        String.class
                ));
    }

    @Test
    void topicShouldReturnOutboxTopic() {
        ImPolicyKafkaOutboxHandler handler =
                new ImPolicyKafkaOutboxHandler(mock(ImPolicyEventDispatchApplicationService.class), OUTBOX_TOPIC);

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateEventIdKeyAndPayloadToApplicationService() {
        ImPolicyEventDispatchApplicationService applicationService = mock(ImPolicyEventDispatchApplicationService.class);
        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(new OutboxEvent(UUID.randomUUID(), "evt-policy-1", OUTBOX_TOPIC, "key-1", "{\"kind\":\"USER_POLICY\"}",
                "PENDING", 0, null, null, null, null));

        ArgumentCaptor<DispatchImPolicyEventCommand> commandCaptor = ArgumentCaptor.forClass(DispatchImPolicyEventCommand.class);
        verify(applicationService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new DispatchImPolicyEventCommand(
                "evt-policy-1",
                "key-1",
                "{\"kind\":\"USER_POLICY\"}"
        ));
    }

    @Test
    void handleShouldPassNullSafeCommandForNullEvent() {
        ImPolicyEventDispatchApplicationService applicationService = mock(ImPolicyEventDispatchApplicationService.class);
        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(null);

        ArgumentCaptor<DispatchImPolicyEventCommand> commandCaptor = ArgumentCaptor.forClass(DispatchImPolicyEventCommand.class);
        verify(applicationService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new DispatchImPolicyEventCommand(null, null, null));
    }
}
