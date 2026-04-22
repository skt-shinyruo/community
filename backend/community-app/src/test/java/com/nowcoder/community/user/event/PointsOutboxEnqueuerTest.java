package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointsOutboxEnqueuerTest {

    @Test
    void postPublishedShouldEnqueuePointsDelta10() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        PointsOutboxEnqueuer enqueuer = new PointsOutboxEnqueuer(objectMapper, store);
        UUID userId = uuid(7);
        UUID postId = uuid(100);

        PostPayload payload = new PostPayload();
        payload.setUserId(userId);
        payload.setPostId(postId);

        enqueuer.onContentEvent(new ContentContractEvent("evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(store, times(1)).enqueue(eventIdCaptor.capture(), topicCaptor.capture(), eventKeyCaptor.capture(), payloadCaptor.capture());

        assertThat(eventIdCaptor.getValue()).isEqualTo("evt-1:points");
        assertThat(topicCaptor.getValue()).isEqualTo(PointsOutboxHandler.TOPIC);
        assertThat(eventKeyCaptor.getValue()).isEqualTo(userId.toString());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("delta").asInt()).isEqualTo(10);
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-1");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(ContentEventTypes.POST_PUBLISHED);
    }

    @Test
    void commentCreatedShouldEnqueuePointsDelta2() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        PointsOutboxEnqueuer enqueuer = new PointsOutboxEnqueuer(objectMapper, store);
        UUID userId = uuid(3);
        UUID commentId = uuid(200);

        CommentPayload payload = new CommentPayload();
        payload.setUserId(userId);
        payload.setCommentId(commentId);

        enqueuer.onContentEvent(new ContentContractEvent("evt-2", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-2:points"), org.mockito.ArgumentMatchers.eq(PointsOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq(userId.toString()), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("delta").asInt()).isEqualTo(2);
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-2");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
    }

    @Test
    void likeCreatedShouldEnqueuePointsDelta1ToEntityUser() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        PointsOutboxEnqueuer enqueuer = new PointsOutboxEnqueuer(objectMapper, store);
        UUID actorUserId = uuid(1);
        UUID entityUserId = uuid(2);
        UUID entityId = uuid(99);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);
        payload.setEntityId(entityId);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-3", SocialEventTypes.LIKE_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-3:points"), org.mockito.ArgumentMatchers.eq(PointsOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq(entityUserId.toString()), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asText()).isEqualTo(entityUserId.toString());
        assertThat(json.path("delta").asInt()).isEqualTo(1);
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-3");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(SocialEventTypes.LIKE_CREATED);
    }

    @Test
    void likeCreatedShouldNotEnqueueWhenSelfLike() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);

        PointsOutboxEnqueuer enqueuer = new PointsOutboxEnqueuer(objectMapper, store);
        UUID userId = uuid(5);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(userId);
        payload.setEntityUserId(userId);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-4", SocialEventTypes.LIKE_CREATED, payload));

        verify(store, times(0)).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
