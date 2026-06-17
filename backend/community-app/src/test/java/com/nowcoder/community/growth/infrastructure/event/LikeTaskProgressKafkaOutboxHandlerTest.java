package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.growth.application.TaskProgressOutboxDispatchApplicationService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LikeTaskProgressKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.projection.growth.task.like";

    @Test
    void topicShouldReturnOutboxTopic() {
        LikeTaskProgressKafkaOutboxHandler handler =
                new LikeTaskProgressKafkaOutboxHandler(mock(TaskProgressOutboxDispatchApplicationService.class), OUTBOX_TOPIC);

        assertThat(handler.topic()).isEqualTo(OUTBOX_TOPIC);
    }

    @Test
    void handleShouldDelegateKeyAndPayloadToGrowthApplicationService() {
        TaskProgressOutboxDispatchApplicationService applicationService = mock(TaskProgressOutboxDispatchApplicationService.class);
        LikeTaskProgressKafkaOutboxHandler handler = new LikeTaskProgressKafkaOutboxHandler(applicationService, OUTBOX_TOPIC);

        handler.handle(new OutboxEvent(UUID.randomUUID(), "event-id", OUTBOX_TOPIC, "key-1", "{\"entityId\":\"e\"}",
                "PENDING", 0, null, null, null, null));

        verify(applicationService).dispatchLikeCreated("key-1", "{\"entityId\":\"e\"}");
    }
}
