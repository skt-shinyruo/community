package com.nowcoder.community.user.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

        PostPayload payload = new PostPayload();
        payload.setUserId(7);
        payload.setPostId(100);

        enqueuer.onContentEvent(new ContentContractEvent("evt-1", ContentEventTypes.POST_PUBLISHED, payload));

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(store, times(1)).enqueue(eventIdCaptor.capture(), topicCaptor.capture(), eventKeyCaptor.capture(), payloadCaptor.capture());

        assertThat(eventIdCaptor.getValue()).isEqualTo("evt-1:points");
        assertThat(topicCaptor.getValue()).isEqualTo(PointsOutboxHandler.TOPIC);
        assertThat(eventKeyCaptor.getValue()).isEqualTo("7");

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asInt()).isEqualTo(7);
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

        CommentPayload payload = new CommentPayload();
        payload.setUserId(3);
        payload.setCommentId(200);

        enqueuer.onContentEvent(new ContentContractEvent("evt-2", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-2:points"), org.mockito.ArgumentMatchers.eq(PointsOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq("3"), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asInt()).isEqualTo(3);
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

        LikePayload payload = new LikePayload();
        payload.setActorUserId(1);
        payload.setEntityUserId(2);
        payload.setEntityId(99);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-3", SocialEventTypes.LIKE_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-3:points"), org.mockito.ArgumentMatchers.eq(PointsOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq("2"), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("userId").asInt()).isEqualTo(2);
        assertThat(json.path("delta").asInt()).isEqualTo(1);
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-3");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(SocialEventTypes.LIKE_CREATED);
    }

    @Test
    void likeCreatedShouldNotEnqueueWhenSelfLike() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);

        PointsOutboxEnqueuer enqueuer = new PointsOutboxEnqueuer(objectMapper, store);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(5);
        payload.setEntityUserId(5);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-4", SocialEventTypes.LIKE_CREATED, payload));

        verify(store, times(0)).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
