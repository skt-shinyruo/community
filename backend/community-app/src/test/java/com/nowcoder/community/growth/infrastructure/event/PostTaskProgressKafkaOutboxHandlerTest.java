package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.growth.application.TaskProgressOutboxDispatchApplicationService;
import com.nowcoder.community.growth.application.command.DispatchTaskProgressEventCommand;
import com.nowcoder.community.growth.application.command.TaskProgressDispatchKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PostTaskProgressKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.projection.growth.task.post";

    @Test
    void topicShouldReturnOutboxTopic() {
        PostTaskProgressKafkaOutboxHandler handler =
                new PostTaskProgressKafkaOutboxHandler(mock(TaskProgressOutboxDispatchApplicationService.class), OUTBOX_TOPIC);

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateKeyAndPayloadToGrowthApplicationService() {
        TaskProgressOutboxDispatchApplicationService applicationService = mock(TaskProgressOutboxDispatchApplicationService.class);
        PostTaskProgressKafkaOutboxHandler handler = new PostTaskProgressKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(new OutboxEvent(UUID.randomUUID(), "event-id", OUTBOX_TOPIC, "key-1", "{\"postId\":\"p\"}",
                "PENDING", 0, null, null, null, null));

        ArgumentCaptor<DispatchTaskProgressEventCommand> commandCaptor = ArgumentCaptor.forClass(DispatchTaskProgressEventCommand.class);
        verify(applicationService).dispatch(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).isEqualTo(new DispatchTaskProgressEventCommand(
                TaskProgressDispatchKind.POST_PUBLISHED,
                "key-1",
                "{\"postId\":\"p\"}"
        ));
    }
}
