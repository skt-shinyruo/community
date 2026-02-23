package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.content.score.InMemoryPostScoreQueue;
import com.nowcoder.community.content.score.PostScoreQueue;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PostScoreQueue postScoreQueue = new InMemoryPostScoreQueue();
    private final SocialEventConsumer consumer = new SocialEventConsumer(
            objectMapper,
            postScoreQueue,
            null,
            "memory",
            "SKIP",
            "DLQ"
    );

    @Test
    void likeCreatedShouldEnqueuePostScoreRefresh() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-like-1",
                "type", SocialEventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 1,
                        "entityType", 1,
                        "entityId", 123,
                        "entityUserId", 2,
                        "postId", 123,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));

        Integer postId = postScoreQueue.pop();
        assertThat(postId).isEqualTo(123);
    }

    @Test
    void likeRemovedShouldEnqueuePostScoreRefresh() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-like-2",
                "type", SocialEventTypes.LIKE_REMOVED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 1,
                        "entityType", 1,
                        "entityId", 123,
                        "entityUserId", 2,
                        "postId", 123,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k2", payload));

        Integer postId = postScoreQueue.pop();
        assertThat(postId).isEqualTo(123);
    }
}
