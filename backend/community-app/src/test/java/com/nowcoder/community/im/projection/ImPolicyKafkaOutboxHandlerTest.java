package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.service.UserModerationService;
import com.nowcoder.community.social.block.BlockService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImPolicyKafkaOutboxHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void moderationOutboxShouldPublishCurrentPolicyState() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UserModerationService moderationService = mock(UserModerationService.class);
        BlockService blockService = mock(BlockService.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(moderationService.getModerationState(uuid(7)))
                .thenReturn(new UserModerationStateView(uuid(7), Instant.now().plusSeconds(60), null));
        when(userLookupQueryApi.getSummaryById(uuid(7))).thenReturn(new UserSummaryView(uuid(7), "u7", "/avatar.png", 0));
        when(kafkaTemplate.send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any()))
                .thenReturn(completedSend());

        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
                objectMapper,
                moderationService,
                blockService,
                userLookupQueryApi,
                kafkaTemplate
        );

        handler.handle(new OutboxEvent(
                UUID.randomUUID(),
                "evt-policy-1",
                ImPolicyKafkaOutboxHandler.TOPIC,
                uuid(7).toString(),
                "{\"kind\":\"MODERATION\",\"userId\":\"" + uuid(7) + "\"}",
                "PENDING",
                0,
                null,
                null
        ));

        verify(kafkaTemplate).send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any(UserMessagingPolicyChanged.class));
    }

    @Test
    void kafkaPublishFailureShouldFailOutboxHandlingForRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UserModerationService moderationService = mock(UserModerationService.class);
        BlockService blockService = mock(BlockService.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(moderationService.getModerationState(uuid(7)))
                .thenReturn(new UserModerationStateView(uuid(7), Instant.now().plusSeconds(60), null));
        when(userLookupQueryApi.getSummaryById(uuid(7))).thenReturn(new UserSummaryView(uuid(7), "u7", "/avatar.png", 0));
        when(kafkaTemplate.send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any()))
                .thenReturn(failedSend());

        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
                objectMapper,
                moderationService,
                blockService,
                userLookupQueryApi,
                kafkaTemplate
        );

        assertThatThrownBy(() -> handler.handle(new OutboxEvent(
                UUID.randomUUID(),
                "evt-policy-1",
                ImPolicyKafkaOutboxHandler.TOPIC,
                uuid(7).toString(),
                "{\"kind\":\"MODERATION\",\"userId\":\"" + uuid(7) + "\"}",
                "PENDING",
                0,
                null,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("im policy kafka publish failed");
    }

    private CompletableFuture<SendResult<String, Object>> completedSend() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, Object>> failedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        return future;
    }
}
