package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.service.UnifiedGrantService;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsOutboxHandlerTest {

    @Test
    void handlerShouldCallPointsServiceWithSourceEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UnifiedGrantService unifiedGrantService = mock(UnifiedGrantService.class);

        PointsOutboxHandler handler = new PointsOutboxHandler(objectMapper, unifiedGrantService);

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

        verify(unifiedGrantService).applyGrant(
                7,
                "src-1:points",
                "PostPublished",
                "src-1",
                "PostPublished",
                10,
                0,
                "points",
                "outbox-event"
        );
    }
}
