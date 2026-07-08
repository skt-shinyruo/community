package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LikeTaskProgressKafkaOutboxEnqueuerTest {

    @Test
    void likeCreatedShouldEnqueueGrowthTaskProjectionWithStableEventId() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        String topic = "custom.projection.growth.task.like";
        UUID actorUserId = uuid(1);
        UUID entityId = uuid(100);
        UUID entityUserId = uuid(2);
        Instant createTime = Instant.parse("2026-05-18T10:30:00Z");

        LikeTaskProgressKafkaOutboxEnqueuer enqueuer =
                new LikeTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, topic);
        enqueuer.onSocialEvent(new SocialContractEvent("local-like-event", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, likePayload(actorUserId, entityId, entityUserId, createTime)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("like-created:like:" + actorUserId + ":" + POST + ":" + entityId + ":growth_task"),
                eq(topic),
                eq(entityUserId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("actorUserId").asText()).isEqualTo(actorUserId.toString());
        assertThat(json.path("entityType").asInt()).isEqualTo(POST);
        assertThat(json.path("entityId").asText()).isEqualTo(entityId.toString());
        assertThat(json.path("entityUserId").asText()).isEqualTo(entityUserId.toString());
        assertThat(json.path("createTime").asText()).isEqualTo(createTime.toString());
    }

    @Test
    void likeCreatedWithoutRelationKeyShouldEnqueueWithFallbackEventId() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        String topic = "custom.projection.growth.task.like";
        UUID actorUserId = uuid(1);
        UUID entityId = uuid(100);
        UUID entityUserId = uuid(2);
        LikePayload payload = likePayload(actorUserId, entityId, entityUserId, Instant.parse("2026-05-18T10:30:00Z"));
        payload.setRelationKey(null);

        LikeTaskProgressKafkaOutboxEnqueuer enqueuer =
                new LikeTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, topic);
        enqueuer.onSocialEvent(new SocialContractEvent("local-like-event", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, payload));

        verify(store).enqueue(
                eq("like-created:" + actorUserId + ":" + POST + ":" + entityId + ":growth_task"),
                eq(topic),
                eq(entityUserId.toString()),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void likeRemovedShouldEnqueueGrowthTaskProjectionWithStableEventId() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        String topic = "custom.projection.growth.task.like";
        UUID actorUserId = uuid(1);
        UUID entityId = uuid(100);
        UUID entityUserId = uuid(2);
        Instant createTime = Instant.parse("2026-05-18T10:30:00Z");

        LikeTaskProgressKafkaOutboxEnqueuer enqueuer =
                new LikeTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, topic);
        enqueuer.onSocialEvent(new SocialContractEvent("local-like-removed-event", null, null, SocialEventTypes.LIKE_REMOVED, java.time.Instant.EPOCH, 1L, likePayload(actorUserId, entityId, entityUserId, createTime)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("like-removed:like:" + actorUserId + ":" + POST + ":" + entityId + ":growth_task"),
                eq(topic),
                eq(entityUserId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("entityUserId").asText()).isEqualTo(entityUserId.toString());
        assertThat(json.path("relationKey").asText()).isEqualTo("like:" + actorUserId + ":" + POST + ":" + entityId);
    }

    @Test
    void selfLikeShouldNotEnqueueGrowthTaskProjection() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        UUID userId = uuid(1);
        LikeTaskProgressKafkaOutboxEnqueuer enqueuer =
                new LikeTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, "topic");

        enqueuer.onSocialEvent(new SocialContractEvent("local-like-event", null, null, SocialEventTypes.LIKE_CREATED, java.time.Instant.EPOCH, 1L, likePayload(userId, uuid(100), userId, Instant.parse("2026-05-18T10:30:00Z"))));

        verifyNoInteractions(store);
    }

    private static LikePayload likePayload(UUID actorUserId, UUID entityId, UUID entityUserId, Instant createTime) {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId);
        payload.setRelationKey("like:" + actorUserId + ":" + POST + ":" + entityId);
        payload.setCreateTime(createTime);
        return payload;
    }
}
