package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.wallet.service.WalletRewardService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsOutboxHandlerTest {

    @Test
    void handlerShouldCallPointsProjectionActionWithSourceEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WalletRewardService walletRewardService = mock(WalletRewardService.class);

        PointsOutboxHandler handler = new PointsOutboxHandler(objectMapper, walletRewardService);

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

        verify(walletRewardService).applyDelta(
                "wallet-reward:src-1",
                7,
                10,
                "PostPublished"
        );
    }
}
