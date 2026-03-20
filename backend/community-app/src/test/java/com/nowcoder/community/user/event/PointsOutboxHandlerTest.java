package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import com.nowcoder.community.user.service.PointsService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsOutboxHandlerTest {

    @Test
    void handlerShouldCallPointsServiceWithSourceEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PointsService pointsService = mock(PointsService.class);

        PointsOutboxHandler handler = new PointsOutboxHandler(objectMapper, pointsService);

        String payloadJson = objectMapper.writeValueAsString(java.util.Map.of(
                "userId", 7,
                "delta", 10,
                "sourceEventId", "src-1",
                "sourceEventType", "PostPublished"
        ));

        OutboxEvent event = new OutboxEvent(
                1L,
                "src-1:points",
                PointsOutboxHandler.TOPIC,
                "7",
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        verify(pointsService).applyPoints(eq(7), eq("src-1"), eq("PostPublished"), eq(10));
    }
}

