package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.JdbcOutboxEventStore;
import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PostTaskProgressKafkaOutboxEnqueuerTest {

    @Test
    void postPublishedShouldEnqueueGrowthTaskProjectionWithStableEventId() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        String topic = "custom.projection.growth.task.post";
        UUID postId = uuid(100);
        UUID userId = uuid(7);
        Instant createTime = Instant.parse("2026-05-18T08:30:00Z");

        PostTaskProgressKafkaOutboxEnqueuer enqueuer =
                new PostTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, topic);
        enqueuer.onContentEvent(new ContentContractEvent("local-post-event", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, postPayload(postId, userId, createTime)));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(store).enqueue(
                eq("post-published:" + postId + ":growth_task"),
                eq(topic),
                eq(userId.toString()),
                payloadCaptor.capture()
        );
        JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(json.path("postId").asText()).isEqualTo(postId.toString());
        assertThat(json.path("userId").asText()).isEqualTo(userId.toString());
        assertThat(json.path("createTime").asText()).isEqualTo(createTime.toString());
    }

    @Test
    void postEventWithoutRequiredFieldsShouldNotEnqueue() {
        JdbcOutboxEventStore store = mock(JdbcOutboxEventStore.class);
        PostTaskProgressKafkaOutboxEnqueuer enqueuer =
                new PostTaskProgressKafkaOutboxEnqueuer(new JacksonJsonCodec(JsonMappers.standard()), store, "topic");

        enqueuer.onContentEvent(new ContentContractEvent("local-post-event", null, null, ContentEventTypes.POST_PUBLISHED, java.time.Instant.EPOCH, 1L, new PostPayload()));

        verifyNoInteractions(store);
    }

    private static PostPayload postPayload(UUID postId, UUID userId, Instant createTime) {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        return payload;
    }
}
