package com.nowcoder.community.content.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.common.event.EventTypes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "content.storage=memory",
        "content.events.publisher=memory",
        "content.score.refresh.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
class SocialEventConsumerTest {

    @Autowired
    SocialEventConsumer consumer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    com.nowcoder.community.content.score.PostScoreQueue postScoreQueue;

    @Test
    void likeCreatedShouldEnqueuePostScoreRefresh() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-like-1",
                "type", EventTypes.LIKE_CREATED,
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
}
