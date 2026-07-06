package com.nowcoder.community.social.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.social.application.command.DispatchSocialEventCommand;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SocialEventDispatchApplicationServiceTest {

    private final JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
    private final SocialIntegrationEventDispatcher dispatcher = mock(SocialIntegrationEventDispatcher.class);
    private final SocialEventDispatchApplicationService service =
            new SocialEventDispatchApplicationService(jsonCodec, dispatcher);

    @Test
    void serviceShouldOnlyLoadForSocialOutboxKafkaPublisher() {
        ConditionalOnExpression conditional = SocialEventDispatchApplicationService.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void dispatchShouldConvertLikePayloadAndSendThroughPort() {
        UUID actorUserId = uuid(101);
        UUID entityId = uuid(102);
        String key = EntityTypes.POST + ":" + entityId;

        service.dispatch(new DispatchSocialEventCommand(key, toJson(new SocialContractEvent(
                "social:LikeCreated:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId,
                entityId,
                "entity",
                SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH,
                1L,
                likePayload(actorUserId, entityId)
        ))));

        ArgumentCaptor<SocialContractEvent> eventCaptor = ArgumentCaptor.forClass(SocialContractEvent.class);
        verify(dispatcher).dispatch(eq(key), eventCaptor.capture());
        SocialContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("social:LikeCreated:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId);
        assertThat(event.type()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        assertThat(event.payload()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) event.payload()).getActorUserId()).isEqualTo(actorUserId);
        assertThat(((LikePayload) event.payload()).getEntityId()).isEqualTo(entityId);
    }

    @Test
    void dispatchShouldConvertFollowBlockAndUnknownPayloads() {
        UUID actorUserId = uuid(201);
        UUID followedUserId = uuid(202);
        UUID blockedUserId = uuid(203);

        service.dispatch(new DispatchSocialEventCommand(actorUserId.toString(), toJson(new SocialContractEvent(
                "social:FollowCreated:" + actorUserId + ":" + followedUserId,
                followedUserId,
                "entity",
                SocialEventTypes.FOLLOW_CREATED,
                Instant.EPOCH,
                1L,
                followPayload(actorUserId, followedUserId)
        ))));
        service.dispatch(new DispatchSocialEventCommand(actorUserId + ":" + blockedUserId, toJson(new SocialContractEvent(
                "social:BlockRelationChanged:" + actorUserId + ":" + blockedUserId + ":42",
                actorUserId,
                "user",
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                Instant.EPOCH,
                42L,
                blockPayload(actorUserId, blockedUserId)
        ))));
        service.dispatch(new DispatchSocialEventCommand(
                "unknown-key",
                """
                        {"eventId":"social:Unknown:1","aggregateId":"%s","aggregateType":"entity","type":"UnknownSocialEvent","occurredAt":"1970-01-01T00:00:00Z","version":1,"payload":{"value":"kept"}}
                        """.formatted(uuid(204))
        ));

        ArgumentCaptor<SocialContractEvent> eventCaptor = ArgumentCaptor.forClass(SocialContractEvent.class);
        verify(dispatcher).dispatch(eq(actorUserId.toString()), eventCaptor.capture());
        verify(dispatcher).dispatch(eq(actorUserId + ":" + blockedUserId), eventCaptor.capture());
        verify(dispatcher).dispatch(eq("unknown-key"), eventCaptor.capture());
        assertThat(eventCaptor.getAllValues().get(0).payload()).isInstanceOf(FollowPayload.class);
        assertThat(((FollowPayload) eventCaptor.getAllValues().get(0).payload()).getEntityId()).isEqualTo(followedUserId);
        assertThat(eventCaptor.getAllValues().get(1).payload()).isInstanceOf(BlockPayload.class);
        assertThat(((BlockPayload) eventCaptor.getAllValues().get(1).payload()).getBlockedUserId()).isEqualTo(blockedUserId);
        assertThat(eventCaptor.getAllValues().get(2).payload())
                .isInstanceOf(Map.class)
                .extracting(payload -> ((Map<?, ?>) payload).get("value"))
                .isEqualTo("kept");
    }

    @Test
    void dispatchShouldRejectNullCommand() {
        assertThatThrownBy(() -> service.dispatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void dispatchShouldRejectBlankOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload is blank");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", " ")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload is blank");
    }

    @Test
    void dispatchShouldRejectPayloadMissingEventIdForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(
                "key",
                "{\"type\":\"LikeCreated\",\"payload\":{\"actorUserId\":\"" + uuid(301) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing eventId");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(
                "key",
                "{\"eventId\":\" \",\"type\":\"LikeCreated\",\"payload\":{\"actorUserId\":\"" + uuid(301) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing eventId");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectPayloadMissingTypeForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"payload\":{\"actorUserId\":\"" + uuid(302) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing type");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(
                "key",
                "{\"eventId\":\"event-1\",\"type\":\" \",\"payload\":{\"actorUserId\":\"" + uuid(302) + "\"}}"
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing type");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectMissingAggregateMetadata() {
        String payloadJson = """
                {"eventId":"se:1","type":"LikeCreated","payload":{"actorUserId":"%s"}}
                """.formatted(uuid(303));

        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void dispatchShouldRejectMalformedAggregateIdForOutboxRetry() {
        UUID actorUserId = uuid(304);
        UUID entityId = uuid(305);
        String key = EntityTypes.POST + ":" + entityId;
        String payloadJson = """
                {"eventId":"se:bad-aggregate","aggregateId":"not-a-uuid","aggregateType":"entity","type":"%s","occurredAt":"1970-01-01T00:00:00Z","version":1,"payload":{"actorUserId":"%s","entityType":"%s","entityId":"%s","entityUserId":"%s","postId":"%s","createTime":"1970-01-01T00:00:00Z"}}
                """.formatted(SocialEventTypes.LIKE_CREATED, actorUserId, EntityTypes.POST, entityId, uuid(306), entityId);

        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(key, payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload invalid aggregateId");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectMalformedOccurredAtForOutboxRetry() {
        UUID actorUserId = uuid(307);
        UUID entityId = uuid(308);
        String key = EntityTypes.POST + ":" + entityId;
        String payloadJson = """
                {"eventId":"se:bad-occurred-at","aggregateId":"%s","aggregateType":"entity","type":"%s","occurredAt":"not-an-instant","version":1,"payload":{"actorUserId":"%s","entityType":"%s","entityId":"%s","entityUserId":"%s","postId":"%s","createTime":"1970-01-01T00:00:00Z"}}
                """.formatted(entityId, SocialEventTypes.LIKE_CREATED, actorUserId, EntityTypes.POST, entityId, uuid(309), entityId);

        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(key, payloadJson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload invalid occurredAt");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldRejectKnownSocialTypeMissingOrNullPayloadForOutboxRetry() {
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", "{\"eventId\":\"event-1\",\"type\":\"LikeCreated\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", "{\"eventId\":\"event-2\",\"type\":\"LikeRemoved\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", "{\"eventId\":\"event-3\",\"type\":\"FollowCreated\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing payload");
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", "{\"eventId\":\"event-4\",\"type\":\"BlockRelationChanged\",\"payload\":null}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload missing payload");

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchShouldWrapPayloadDeserializationFailure() {
        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand("key", "{not-json")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload deserialization failed");
    }

    @Test
    void dispatchShouldPropagatePortFailureForOutboxRetry() {
        RuntimeException failure = new RuntimeException("kafka down");
        UUID actorUserId = uuid(401);
        UUID entityId = uuid(402);
        String key = EntityTypes.POST + ":" + entityId;
        String payloadJson = toJson(new SocialContractEvent(
                "social:LikeCreated:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId,
                entityId,
                "entity",
                SocialEventTypes.LIKE_CREATED,
                Instant.EPOCH,
                1L,
                likePayload(actorUserId, entityId)
        ));
        doThrow(failure).when(dispatcher).dispatch(eq(key), any());

        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(key, payloadJson)))
                .isSameAs(failure);
    }

    private String toJson(SocialContractEvent event) {
        return jsonCodec.toJson(event);
    }

    private static LikePayload likePayload(UUID actorUserId, UUID entityId) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(uuid(103));
        payload.setPostId(entityId);
        payload.setCreateTime(Instant.EPOCH);
        return payload;
    }

    private static FollowPayload followPayload(UUID actorUserId, UUID followedUserId) {
        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(EntityTypes.USER);
        payload.setEntityId(followedUserId);
        payload.setEntityUserId(followedUserId);
        payload.setCreateTime(Instant.EPOCH);
        return payload;
    }

    private static BlockPayload blockPayload(UUID blockerUserId, UUID blockedUserId) {
        BlockPayload payload = new BlockPayload();
        payload.setBlockerUserId(blockerUserId);
        payload.setBlockedUserId(blockedUserId);
        payload.setBlocked(true);
        payload.setVersion(42L);
        return payload;
    }
}
