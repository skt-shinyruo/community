package com.nowcoder.community.growth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.service.TaskProgressService;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskProgressOutboxHandlerTest {

    @Test
    void handlerShouldCallTaskProgressServiceWithPayloadFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskProgressService taskProgressService = mock(TaskProgressService.class);

        TaskProgressOutboxHandler handler = new TaskProgressOutboxHandler(objectMapper, taskProgressService);

        String payloadJson = objectMapper.writeValueAsString(Map.of(
                "userId", 7,
                "triggerEventType", "PostPublished",
                "sourceEventId", "post-evt-1",
                "bizDate", "2026-03-22"
        ));

        OutboxEvent event = new OutboxEvent(
                1L,
                "post-evt-1:task-progress",
                TaskProgressOutboxHandler.TOPIC,
                "7",
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        verify(taskProgressService).processEvent(7, "PostPublished", "post-evt-1", LocalDate.of(2026, 3, 22));
    }
}
