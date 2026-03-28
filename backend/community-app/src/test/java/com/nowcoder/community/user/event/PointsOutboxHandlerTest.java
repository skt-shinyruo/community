package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.growth.api.action.GrowthGrantActionApi;
import com.nowcoder.community.infra.outbox.OutboxEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsOutboxHandlerTest {

    @Test
    void handlerShouldCallPointsProjectionActionWithSourceEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        GrowthGrantActionApi growthGrantActionApi = mock(GrowthGrantActionApi.class);

        PointsOutboxHandler handler = new PointsOutboxHandler(objectMapper, growthGrantActionApi);

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

        verify(growthGrantActionApi).applyPointsProjection(
                7,
                "src-1",
                "PostPublished",
                10
        );
    }
}
