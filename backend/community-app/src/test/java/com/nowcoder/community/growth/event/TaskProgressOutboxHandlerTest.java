package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.common.outbox.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskProgressOutboxHandlerTest {

    @Test
    void handlerShouldCallTaskProgressServiceWithPayloadFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        UUID userId = uuid(7);

        TaskProgressOutboxHandler handler = new TaskProgressOutboxHandler(objectMapper, taskProgressService);

        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "userId", userId,
                "triggerEventType", "PostPublished",
                "sourceEventId", "post-evt-1",
                "bizDate", "2026-03-22"
        ));

        OutboxEvent event = new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000041"),
                "post-evt-1:task-progress",
                TaskProgressOutboxHandler.TOPIC,
                userId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        verify(taskProgressService).processEvent(userId, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));
    }
}
