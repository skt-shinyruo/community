package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.user.application.UserRewardApplicationService;
import com.nowcoder.community.wallet.api.action.WalletRewardActionApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CommentRewardOutboxHandlerTest {

    private static final String TOPIC = "custom.projection.user.reward.comment";

    @Test
    void handlerShouldAwardCommentRewardThroughUserApplicationService() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardApplicationService applicationService = new UserRewardApplicationService(walletRewardActionApi);
        CommentRewardOutboxHandler handler = new CommentRewardOutboxHandler(objectMapper, applicationService, TOPIC);
        UUID commentId = uuid(200);
        UUID userId = uuid(3);

        handler.handle(outboxEvent(objectMapper, commentId, userId));

        verify(walletRewardActionApi).applyDelta(
                "wallet-reward:comment-created:" + commentId,
                userId,
                2,
                "CommentCreated"
        );
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        WalletRewardActionApi walletRewardActionApi = mock(WalletRewardActionApi.class);
        UserRewardApplicationService applicationService = new UserRewardApplicationService(walletRewardActionApi);
        CommentRewardOutboxHandler handler = new CommentRewardOutboxHandler(objectMapper, applicationService, TOPIC);

        handler.handle(new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000031"),
                "comment-created:blank:user_reward",
                TOPIC,
                "key",
                " ",
                "PENDING",
                0,
                null,
                null,
                null,
                null
        ));

        verifyNoInteractions(walletRewardActionApi);
    }

    private static OutboxEvent outboxEvent(ObjectMapper objectMapper, UUID commentId, UUID userId) throws Exception {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setUserId(userId);
        payload.setCreateTime(Instant.parse("2026-05-18T09:30:00Z"));
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000032"),
                "comment-created:" + commentId + ":user_reward",
                TOPIC,
                userId.toString(),
                objectMapper.writeValueAsString(payload),
                "PENDING",
                0,
                null,
                null,
                null,
                null
        );
    }
}
