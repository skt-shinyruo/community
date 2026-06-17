package com.nowcoder.community.social.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.application.SocialEventDispatchApplicationService;
import com.nowcoder.community.social.application.SocialEventKafkaDispatchPort;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.BlockPayload;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.domain.event.BlockRelationChangedDomainEvent;
import com.nowcoder.community.social.domain.event.FollowCreatedDomainEvent;
import com.nowcoder.community.social.domain.event.LikeChangedDomainEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxSocialDomainEventPublisherTest {

    private static final String TOPIC = "custom.eventbus.social";

    @Test
    void publisherShouldOnlyDefaultWhenOutboxWorkerIsEnabled() {
        ConditionalOnExpression conditional = OutboxSocialDomainEventPublisher.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void localPublisherShouldBeOptInOnly() {
        ConditionalOnProperty conditional = LocalSocialDomainEventPublisher.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.name()).containsExactly("social.events.publisher");
        assertThat(conditional.havingValue()).isEqualTo("local");
        assertThat(conditional.matchIfMissing()).isFalse();
    }

    @Test
    void likeCreatedShouldWriteSocialContractEnvelopeToOutbox() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID actorUserId = uuid(1);
        UUID entityId = uuid(10);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                actorUserId, EntityTypes.POST, entityId, uuid(2), entityId, true, Instant.EPOCH
        ));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                eq(EntityTypes.POST + ":" + entityId),
                payloadCaptor.capture()
        );
        String eventId = eventIdCaptor.getValue();
        assertThat(eventId).startsWith("se:like:created:");
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("type").asText()).isEqualTo(SocialEventTypes.LIKE_CREATED);
        assertThat(json.path("payload").path("actorUserId").asText()).isEqualTo(actorUserId.toString());
        assertThat(json.path("payload").path("entityType").asInt()).isEqualTo(EntityTypes.POST);
        assertThat(json.path("payload").path("entityId").asText()).isEqualTo(entityId.toString());
    }

    @Test
    void likeRemovedShouldUseRemovedEventIdAndEntityKey() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID actorUserId = uuid(3);
        UUID entityId = uuid(30);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                actorUserId, EntityTypes.COMMENT, entityId, uuid(4), uuid(40), false, Instant.EPOCH
        ));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                eq(EntityTypes.COMMENT + ":" + entityId),
                payloadCaptor.capture()
        );
        String eventId = eventIdCaptor.getValue();
        assertThat(eventId).startsWith("se:like:removed:");
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("type").asText()).isEqualTo(SocialEventTypes.LIKE_REMOVED);
    }

    @Test
    void followCreatedShouldUseActorEntityEventIdAndEntityKey() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID actorUserId = uuid(5);
        UUID entityId = uuid(50);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishFollowCreated(new FollowCreatedDomainEvent(
                actorUserId, EntityTypes.USER, entityId, entityId, Instant.EPOCH
        ));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                eq(EntityTypes.USER + ":" + entityId),
                payloadCaptor.capture()
        );
        String eventId = eventIdCaptor.getValue();
        assertThat(eventId).startsWith("se:follow:created:");
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("type").asText()).isEqualTo(SocialEventTypes.FOLLOW_CREATED);
        assertThat(json.path("payload").path("entityUserId").asText()).isEqualTo(entityId.toString());
    }

    @Test
    void publishedSocialOutboxPayloadsShouldDispatchAsTypedKafkaContractEvents() {
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        SocialEventKafkaDispatchPort dispatchPort = mock(SocialEventKafkaDispatchPort.class);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(jsonCodec, store, TOPIC);
        SocialEventDispatchApplicationService dispatchService =
                new SocialEventDispatchApplicationService(jsonCodec, dispatchPort, "social.events");
        UUID likedPostId = uuid(101);
        UUID removedCommentId = uuid(102);
        UUID followedUserId = uuid(103);
        UUID blockerUserId = uuid(104);
        UUID blockedUserId = uuid(105);

        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                uuid(201), EntityTypes.POST, likedPostId, uuid(301), likedPostId, true, Instant.EPOCH
        ));
        publisher.publishLikeChanged(new LikeChangedDomainEvent(
                uuid(202), EntityTypes.COMMENT, removedCommentId, uuid(302), uuid(402), false, Instant.EPOCH
        ));
        publisher.publishFollowCreated(new FollowCreatedDomainEvent(
                uuid(203), EntityTypes.USER, followedUserId, followedUserId, Instant.EPOCH
        ));
        publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(
                blockerUserId, blockedUserId, true, 99L
        ));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store, times(4)).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                keyCaptor.capture(),
                payloadCaptor.capture()
        );
        for (int i = 0; i < payloadCaptor.getAllValues().size(); i++) {
            dispatchService.dispatch(keyCaptor.getAllValues().get(i), payloadCaptor.getAllValues().get(i));
        }

        ArgumentCaptor<String> dispatchedKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SocialContractEvent> eventCaptor = ArgumentCaptor.forClass(SocialContractEvent.class);
        verify(dispatchPort, times(4)).send(eq("social.events"), dispatchedKeyCaptor.capture(), eventCaptor.capture());
        assertThat(dispatchedKeyCaptor.getAllValues()).containsExactlyElementsOf(keyCaptor.getAllValues());
        assertThat(eventCaptor.getAllValues())
                .extracting(SocialContractEvent::eventId)
                .containsExactlyElementsOf(eventIdCaptor.getAllValues());
        assertThat(eventCaptor.getAllValues())
                .extracting(SocialContractEvent::type)
                .containsExactly(
                        SocialEventTypes.LIKE_CREATED,
                        SocialEventTypes.LIKE_REMOVED,
                        SocialEventTypes.FOLLOW_CREATED,
                        SocialEventTypes.BLOCK_RELATION_CHANGED
                );
        assertThat(eventCaptor.getAllValues().get(0).payload()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) eventCaptor.getAllValues().get(0).payload()).getEntityId()).isEqualTo(likedPostId);
        assertThat(eventCaptor.getAllValues().get(1).payload()).isInstanceOf(LikePayload.class);
        assertThat(((LikePayload) eventCaptor.getAllValues().get(1).payload()).getEntityId()).isEqualTo(removedCommentId);
        assertThat(eventCaptor.getAllValues().get(2).payload()).isInstanceOf(FollowPayload.class);
        assertThat(((FollowPayload) eventCaptor.getAllValues().get(2).payload()).getEntityId()).isEqualTo(followedUserId);
        assertThat(eventCaptor.getAllValues().get(3).payload()).isInstanceOf(BlockPayload.class);
        assertThat(((BlockPayload) eventCaptor.getAllValues().get(3).payload()).getBlockedUserId()).isEqualTo(blockedUserId);
    }

    @Test
    void blockRelationChangedShouldWriteVersionedContractEnvelopeToOutbox() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID blockerUserId = uuid(6);
        UUID blockedUserId = uuid(7);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(blockerUserId, blockedUserId, true, 81L));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                eq(blockerUserId.toString()),
                payloadCaptor.capture()
        );
        String eventId = eventIdCaptor.getValue();
        assertThat(eventId).startsWith("se:block:");
        assertThat(eventId.length()).isLessThanOrEqualTo(64);
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo(eventId);
        assertThat(json.path("type").asText()).isEqualTo(SocialEventTypes.BLOCK_RELATION_CHANGED);
        assertThat(json.path("payload").path("blockerUserId").asText()).isEqualTo(blockerUserId.toString());
        assertThat(json.path("payload").path("blockedUserId").asText()).isEqualTo(blockedUserId.toString());
        assertThat(json.path("payload").path("blocked").asBoolean()).isTrue();
        assertThat(json.path("payload").path("version").asLong()).isEqualTo(81L);
    }

    @Test
    void repeatedSocialOperationsShouldUseDistinctShortEventIds() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID actorUserId = uuid(8);
        UUID entityId = uuid(80);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );
        LikeChangedDomainEvent event = new LikeChangedDomainEvent(
                actorUserId, EntityTypes.POST, entityId, uuid(2), entityId, true, Instant.EPOCH
        );

        publisher.publishLikeChanged(event);
        publisher.publishLikeChanged(event);

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(store, times(2)).enqueue(
                eventIdCaptor.capture(),
                eq(TOPIC),
                eq(EntityTypes.POST + ":" + entityId),
                any()
        );
        List<String> eventIds = eventIdCaptor.getAllValues();
        assertThat(eventIds).doesNotHaveDuplicates();
        assertThat(eventIds).allSatisfy(eventId -> {
            assertThat(eventId).startsWith("se:like:created:");
            assertThat(eventId.length()).isLessThanOrEqualTo(64);
        });
    }

    @Test
    void eventsWithoutRequiredIdsShouldNotEnqueue() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(
                new JacksonJsonCodec(JsonMappers.standard()),
                store,
                TOPIC
        );

        publisher.publishLikeChanged(null);
        publisher.publishLikeChanged(new LikeChangedDomainEvent(null, EntityTypes.POST, uuid(10), uuid(2), uuid(10), true, Instant.EPOCH));
        publisher.publishLikeChanged(new LikeChangedDomainEvent(uuid(1), EntityTypes.POST, null, uuid(2), uuid(10), true, Instant.EPOCH));
        publisher.publishFollowCreated(new FollowCreatedDomainEvent(null, EntityTypes.USER, uuid(2), uuid(2), Instant.EPOCH));
        publisher.publishFollowCreated(new FollowCreatedDomainEvent(uuid(1), EntityTypes.USER, null, uuid(2), Instant.EPOCH));
        publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(null, uuid(2), true, 0L));
        publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(uuid(1), null, true, 0L));

        verifyNoInteractions(store);
    }

    @Test
    void serializationFailureShouldThrowRetryVisibleException() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        JsonCodec failingJsonCodec = new JsonCodec() {
            @Override
            public String toJson(Object value) {
                throw new JsonCodecException("boom", new RuntimeException("boom"));
            }

            @Override
            public <T> T fromJson(String json, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode readTree(String json) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T treeToValue(com.fasterxml.jackson.databind.JsonNode node, Class<T> type) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.fasterxml.jackson.databind.JsonNode valueToTree(Object value) {
                throw new UnsupportedOperationException();
            }
        };
        OutboxSocialDomainEventPublisher publisher = new OutboxSocialDomainEventPublisher(failingJsonCodec, store, TOPIC);

        assertThatThrownBy(() -> publisher.publishBlockRelationChanged(new BlockRelationChangedDomainEvent(uuid(1), uuid(2), true, 0L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event outbox payload serialization failed");
        verifyNoInteractions(store);
    }
}
