package com.nowcoder.community.message.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.infra.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.FollowPayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeOutboxEnqueuerTest {

    @Test
    void commentCreatedShouldEnqueueCommentNotice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        NoticeOutboxEnqueuer enqueuer = new NoticeOutboxEnqueuer(objectMapper, store);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(9);
        payload.setPostId(100);
        payload.setCommentId(200);

        enqueuer.onContentEvent(new ContentContractEvent("evt-n1", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-n1:notice"), org.mockito.ArgumentMatchers.eq(NoticeOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq("9"), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("toUserId").asInt()).isEqualTo(9);
        assertThat(json.path("topic").asText()).isEqualTo("comment");
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-n1");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(json.path("payload").path("commentId").asInt()).isEqualTo(200);
    }

    @Test
    void followCreatedShouldEnqueueFollowNotice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        NoticeOutboxEnqueuer enqueuer = new NoticeOutboxEnqueuer(objectMapper, store);

        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(1);
        payload.setEntityUserId(2);
        payload.setEntityId(300);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-n2", SocialEventTypes.FOLLOW_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-n2:notice"), org.mockito.ArgumentMatchers.eq(NoticeOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq("2"), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("toUserId").asInt()).isEqualTo(2);
        assertThat(json.path("topic").asText()).isEqualTo("follow");
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-n2");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(SocialEventTypes.FOLLOW_CREATED);
        assertThat(json.path("payload").path("entityId").asInt()).isEqualTo(300);
    }

    @Test
    void commentCreatedShouldNotEnqueueWhenTargetUserIdMissing() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);

        NoticeOutboxEnqueuer enqueuer = new NoticeOutboxEnqueuer(objectMapper, store);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(null);

        enqueuer.onContentEvent(new ContentContractEvent("evt-n3", ContentEventTypes.COMMENT_CREATED, payload));

        verify(store, times(0)).enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
