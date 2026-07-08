package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
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

class CommentTaskProgressOutboxEnqueuerTest {

    @Test
    void commentCreatedShouldEnqueueGrowthTaskProjectionWithStableEventId() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        when(store.enqueue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        String topic = "custom.projection.growth.task.comment";
        UUID commentId = uuid(200);
        UUID userId = uuid(3);
        Instant createTime = Instant.parse("2026-05-18T09:30:00Z");

        CommentTaskProgressOutboxEnqueuer enqueuer =
                new CommentTaskProgressOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, topic);
        enqueuer.onContentEvent(new ContentContractEvent("random-local-event-id", null, null, ContentEventTypes.COMMENT_CREATED, java.time.Instant.EPOCH, 1L, commentPayload(commentId, userId, createTime)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("comment-created:" + commentId + ":growth_task"),
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
