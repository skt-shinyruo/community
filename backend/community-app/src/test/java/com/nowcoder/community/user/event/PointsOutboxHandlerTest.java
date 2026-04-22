package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.wallet.service.WalletRewardService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PointsOutboxHandlerTest {

    @Test
    void handlerShouldCallPointsProjectionActionWithSourceEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WalletRewardService walletRewardService = mock(WalletRewardService.class);
        UUID userId = uuid(7);

        PointsOutboxHandler handler = new PointsOutboxHandler(objectMapper, walletRewardService);

        String payloadJson = objectMapper.writeValueAsString(java.util.Map.of(
                "userId", userId.toString(),
                "delta", 10,
                "sourceEventId", "src-1",
                "sourceEventType", "PostPublished"
        ));

        OutboxEvent event = new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000011"),
                "src-1:points",
                PointsOutboxHandler.TOPIC,
                userId.toString(),
                payloadJson,
                "PENDING",
                0,
                null,
                null
        );

        handler.handle(event);

        verify(walletRewardService).applyDelta(
                "wallet-reward:src-1",
                userId,
                10,
                "PostPublished"
        );
    }
}
