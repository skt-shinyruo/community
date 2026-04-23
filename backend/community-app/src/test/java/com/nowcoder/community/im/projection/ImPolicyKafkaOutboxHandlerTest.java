package com.nowcoder.community.im.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.UserBlockRelationChanged;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import com.nowcoder.community.social.api.query.SocialBlockQueryApi;
import com.nowcoder.community.user.api.model.UserModerationStateView;
import com.nowcoder.community.user.api.model.UserSummaryView;
import com.nowcoder.community.user.api.query.UserLookupQueryApi;
import com.nowcoder.community.user.api.query.UserModerationQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImPolicyKafkaOutboxHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void handlerShouldOnlyExposeOwnerDomainQueryApiConstructor() {
        assertThat(ImPolicyKafkaOutboxHandler.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes()).containsExactly(
                        ObjectMapper.class,
                        UserModerationQueryApi.class,
                        SocialBlockQueryApi.class,
                        UserLookupQueryApi.class,
                        KafkaTemplate.class
                ));
    }

    @Test
    void moderationOutboxShouldPublishCurrentPolicyState() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi socialBlockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        Instant muteUntil = Instant.parse("2026-04-24T09:15:30Z");
        Instant expiredBanUntil = Instant.parse("2026-04-22T09:15:30Z");
        when(moderationQueryApi.getModerationState(uuid(7)))
                .thenReturn(new UserModerationStateView(uuid(7), muteUntil, expiredBanUntil));
        when(userLookupQueryApi.getSummaryById(uuid(7))).thenReturn(new UserSummaryView(uuid(7), "u7", "/avatar.png", 0));
        when(kafkaTemplate.send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any()))
                .thenReturn(completedSend());

        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
                objectMapper,
                moderationQueryApi,
                socialBlockQueryApi,
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

        ArgumentCaptor<UserMessagingPolicyChanged> policyCaptor = ArgumentCaptor.forClass(UserMessagingPolicyChanged.class);
        verify(kafkaTemplate).send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), policyCaptor.capture());
        UserMessagingPolicyChanged published = policyCaptor.getValue();
        assertThat(published.userId()).isEqualTo(uuid(7));
        assertThat(published.userExists()).isTrue();
        assertThat(published.muted()).isTrue();
        assertThat(published.suspended()).isFalse();
        assertThat(recordComponentValue(published, "muteUntil")).isEqualTo(muteUntil.toEpochMilli());
        assertThat(recordComponentValue(published, "banUntil")).isEqualTo(expiredBanUntil.toEpochMilli());
    }

    @Test
    void blockOutboxShouldPublishCurrentBlockState() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi socialBlockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(socialBlockQueryApi.hasBlocked(uuid(7), uuid(8))).thenReturn(true);
        when(kafkaTemplate.send(eq(ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED), eq(uuid(7).toString()), any()))
                .thenReturn(completedSend());

        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
                objectMapper,
                moderationQueryApi,
                socialBlockQueryApi,
                userLookupQueryApi,
                kafkaTemplate
        );

        handler.handle(new OutboxEvent(
                UUID.randomUUID(),
                "evt-policy-2",
                ImPolicyKafkaOutboxHandler.TOPIC,
                uuid(7).toString(),
                "{\"kind\":\"BLOCK\",\"primaryUserId\":\"" + uuid(7) + "\",\"secondaryUserId\":\"" + uuid(8) + "\"}",
                "PENDING",
                0,
                null,
                null
        ));

        verify(kafkaTemplate).send(eq(ImTopics.EVENT_USER_BLOCK_RELATION_CHANGED), eq(uuid(7).toString()), any(UserBlockRelationChanged.class));
    }

    @Test
    void kafkaPublishFailureShouldFailOutboxHandlingForRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        UserModerationQueryApi moderationQueryApi = mock(UserModerationQueryApi.class);
        SocialBlockQueryApi socialBlockQueryApi = mock(SocialBlockQueryApi.class);
        UserLookupQueryApi userLookupQueryApi = mock(UserLookupQueryApi.class);
        when(moderationQueryApi.getModerationState(uuid(7)))
                .thenReturn(new UserModerationStateView(uuid(7), Instant.now().plusSeconds(60), null));
        when(userLookupQueryApi.getSummaryById(uuid(7))).thenReturn(new UserSummaryView(uuid(7), "u7", "/avatar.png", 0));
        when(kafkaTemplate.send(eq(ImTopics.EVENT_USER_MESSAGING_POLICY_CHANGED), eq(uuid(7).toString()), any()))
                .thenReturn(failedSend());

        ImPolicyKafkaOutboxHandler handler = new ImPolicyKafkaOutboxHandler(
                objectMapper,
                moderationQueryApi,
                socialBlockQueryApi,
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

    private static Object recordComponentValue(Object record, String componentName) {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(componentName)) {
                try {
                    return component.getAccessor().invoke(record);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("cannot read component: " + componentName, e);
                }
            }
        }
        throw new AssertionError(record.getClass().getSimpleName() + " missing component: " + componentName);
    }
}
