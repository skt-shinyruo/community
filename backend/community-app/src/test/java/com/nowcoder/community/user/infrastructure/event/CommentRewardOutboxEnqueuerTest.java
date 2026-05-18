package com.nowcoder.community.user.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentRewardOutboxEnqueuerTest {

    @Test
    void commentCreatedShouldEnqueueUserRewardProjectionWithStableEventId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        String topic = "custom.projection.user.reward.comment";
        UUID commentId = uuid(200);
        UUID userId = uuid(3);
        Instant createTime = Instant.parse("2026-05-18T09:30:00Z");

        CommentRewardOutboxEnqueuer enqueuer = new CommentRewardOutboxEnqueuer(objectMapper, store, topic);
        enqueuer.onContentEvent(new ContentContractEvent("random-local-event-id", ContentEventTypes.COMMENT_CREATED,
                commentPayload(commentId, userId, createTime)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("comment-created:" + commentId + ":user_reward"),
                eq(topic),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("commentId").asText()).isEqualTo(commentId.toString());
        assertThat(json.path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("createTime").asText()).isEqualTo(createTime.toString());
    }

    private static CommentPayload commentPayload(UUID commentId, UUID userId, Instant createTime) {
        CommentPayload payload = new CommentPayload();
        payload.setCommentId(commentId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        return payload;
    }
}
