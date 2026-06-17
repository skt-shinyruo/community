package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.im.application.ImPolicyEventDispatchApplicationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        verify(applicationService).dispatch("evt-policy-1", "key-1", "{\"kind\":\"USER_POLICY\"}");
    }

    @Test
    void handleShouldIgnoreNullEvent() {
        ImPolicyEventDispatchApplicationService applicationService = mock(ImPolicyEventDispatchApplicationService.class);
        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(null);

        verifyNoInteractions(applicationService);
    }
}
