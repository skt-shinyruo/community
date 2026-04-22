package com.nowcoder.community.notice.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.social.contracts.event.FollowPayload;
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

class NoticeOutboxEnqueuerTest {

    @Test
    void commentCreatedShouldEnqueueCommentNotice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        NoticeOutboxEnqueuer enqueuer = new NoticeOutboxEnqueuer(objectMapper, store);
        UUID targetUserId = uuid(9);
        UUID postId = uuid(100);
        UUID commentId = uuid(200);

        CommentPayload payload = new CommentPayload();
        payload.setTargetUserId(targetUserId);
        payload.setPostId(postId);
        payload.setCommentId(commentId);

        enqueuer.onContentEvent(new ContentContractEvent("evt-n1", ContentEventTypes.COMMENT_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-n1:notice"), org.mockito.ArgumentMatchers.eq(NoticeOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq(targetUserId.toString()), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("toUserId").asText()).isEqualTo(targetUserId.toString());
        assertThat(json.path("topic").asText()).isEqualTo("comment");
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-n1");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(ContentEventTypes.COMMENT_CREATED);
        assertThat(json.path("payload").path("commentId").asText()).isEqualTo(commentId.toString());
    }

    @Test
    void followCreatedShouldEnqueueFollowNotice() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        NoticeOutboxEnqueuer enqueuer = new NoticeOutboxEnqueuer(objectMapper, store);
        UUID actorUserId = uuid(1);
        UUID entityUserId = uuid(2);
        UUID entityId = uuid(300);

        FollowPayload payload = new FollowPayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityUserId(entityUserId);
        payload.setEntityId(entityId);

        enqueuer.onSocialEvent(new SocialContractEvent("evt-n2", SocialEventTypes.FOLLOW_CREATED, payload));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(org.mockito.ArgumentMatchers.eq("evt-n2:notice"), org.mockito.ArgumentMatchers.eq(NoticeOutboxHandler.TOPIC), org.mockito.ArgumentMatchers.eq(entityUserId.toString()), payloadCaptor.capture());

        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("toUserId").asText()).isEqualTo(entityUserId.toString());
        assertThat(json.path("topic").asText()).isEqualTo("follow");
        assertThat(json.path("sourceEventId").asText()).isEqualTo("evt-n2");
        assertThat(json.path("sourceEventType").asText()).isEqualTo(SocialEventTypes.FOLLOW_CREATED);
        assertThat(json.path("payload").path("entityId").asText()).isEqualTo(entityId.toString());
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
