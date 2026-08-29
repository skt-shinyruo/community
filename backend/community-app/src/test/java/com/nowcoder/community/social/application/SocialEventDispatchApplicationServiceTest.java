package com.nowcoder.community.social.application;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.social.application.SocialEventDispatchApplicationService.DispatchSocialEventCommand;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialContractEventCodec;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialTypedEvent;
import com.nowcoder.community.social.infrastructure.event.JacksonSocialContractEventCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
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

    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(JacksonJsonCodec.standardMapper());
    private final SocialContractEventCodec contractEventCodec = new JacksonSocialContractEventCodec(jsonCodec);
    private final SocialIntegrationEventDispatcher dispatcher = mock(SocialIntegrationEventDispatcher.class);
    private final SocialEventDispatchApplicationService service =
            new SocialEventDispatchApplicationService(contractEventCodec, dispatcher);

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
                jsonCodec.valueToTree(likePayload(actorUserId, entityId))
        ))));

        ArgumentCaptor<SocialContractEvent> eventCaptor = ArgumentCaptor.forClass(SocialContractEvent.class);
        verify(dispatcher).dispatch(eq(key), eventCaptor.capture());
        SocialContractEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isEqualTo("social:LikeCreated:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId);
        assertThat(event.type()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        SocialTypedEvent.LikeCreated typedEvent = (SocialTypedEvent.LikeCreated) contractEventCodec.decode(event);
        assertThat(typedEvent.payload().actorUserId()).isEqualTo(actorUserId);
        assertThat(typedEvent.payload().entityId()).isEqualTo(entityId);
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
                jsonCodec.valueToTree(followPayload(actorUserId, followedUserId))
        ))));
        service.dispatch(new DispatchSocialEventCommand(actorUserId + ":" + blockedUserId, toJson(new SocialContractEvent(
                "social:BlockRelationChanged:" + actorUserId + ":" + blockedUserId + ":42",
                actorUserId,
                "user",
                SocialEventTypes.BLOCK_RELATION_CHANGED,
                Instant.EPOCH,
                42L,
                jsonCodec.valueToTree(blockPayload(actorUserId, blockedUserId))
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
        SocialTypedEvent.FollowCreated followCreated =
                (SocialTypedEvent.FollowCreated) contractEventCodec.decode(eventCaptor.getAllValues().get(0));
        SocialTypedEvent.BlockRelationChanged blockRelationChanged =
                (SocialTypedEvent.BlockRelationChanged) contractEventCodec.decode(eventCaptor.getAllValues().get(1));
        assertThat(followCreated.payload().entityId()).isEqualTo(followedUserId);
        assertThat(blockRelationChanged.payload().blockedUserId()).isEqualTo(blockedUserId);
        assertThat(eventCaptor.getAllValues().get(2).payload().path("value").asText()).isEqualTo("kept");
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
                {"eventId":"se:bad-aggregate","aggregateId":"not-a-uuid","aggregateType":"entity","type":"%s","occurredAt":"1970-01-01T00:00:00Z","version":1,"payload":{"actorUserId":"%s","entityType":"%s","entityId":"%s","entityUserId":"%s","postId":"%s","relationKey":"%s"}}
                """.formatted(SocialEventTypes.LIKE_CREATED, actorUserId, EntityTypes.POST, entityId, uuid(306), entityId,
                        "like:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId);

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
                {"eventId":"se:bad-occurred-at","aggregateId":"%s","aggregateType":"entity","type":"%s","occurredAt":"not-an-instant","version":1,"payload":{"actorUserId":"%s","entityType":"%s","entityId":"%s","entityUserId":"%s","postId":"%s","relationKey":"%s"}}
                """.formatted(entityId, SocialEventTypes.LIKE_CREATED, actorUserId, EntityTypes.POST, entityId, uuid(309), entityId,
                        "like:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId);

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
                jsonCodec.valueToTree(likePayload(actorUserId, entityId))
        ));
        doThrow(failure).when(dispatcher).dispatch(eq(key), any());

        assertThatThrownBy(() -> service.dispatch(new DispatchSocialEventCommand(key, payloadJson)))
                .isSameAs(failure);
    }

    private String toJson(SocialContractEvent event) {
        return jsonCodec.toJson(event);
    }

    private static LikePayload likePayload(UUID actorUserId, UUID entityId) {
        return new LikePayload(
                actorUserId,
                EntityTypes.POST,
                entityId,
                uuid(103),
                entityId,
                "like:" + actorUserId + ":" + EntityTypes.POST + ":" + entityId,
                null,
                null
        );
    }

    private static FollowPayload followPayload(UUID actorUserId, UUID followedUserId) {
        return new FollowPayload(
                actorUserId,
                EntityTypes.USER,
                followedUserId,
                followedUserId,
                Instant.EPOCH
        );
    }

    private static BlockPayload blockPayload(UUID blockerUserId, UUID blockedUserId) {
        return new BlockPayload(blockerUserId, blockedUserId, true, null, 42L);
    }
}
