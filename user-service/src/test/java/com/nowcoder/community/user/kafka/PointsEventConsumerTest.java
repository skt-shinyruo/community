package com.nowcoder.community.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.social.api.event.SocialEventTypes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.nowcoder.community.user.service.PointsService;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointsEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PointsService pointsService = mock(PointsService.class);
    private final PointsEventConsumer consumer = new PointsEventConsumer(objectMapper, pointsService, "SKIP", "DLQ");

    private final Set<String> appliedEventIds = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> scoreByUserId = new ConcurrentHashMap<>();

    @BeforeEach
    void resetDb() {
        appliedEventIds.clear();
        scoreByUserId.clear();

        // 用 in-memory stub 模拟 PointsService 的“按 eventId 幂等入账”语义，避免依赖 DB/Spring 容器。
        when(pointsService.applyPoints(anyInt(), anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            String eventId = invocation.getArgument(1);
            int delta = invocation.getArgument(3);

            if (eventId == null || eventId.isBlank()) {
                return false;
            }
            if (!appliedEventIds.add(eventId)) {
                return false;
            }
            scoreByUserId.merge(userId, delta, Integer::sum);
            return true;
        });
    }

    @Test
    void postPublishedShouldAddPointsIdempotently() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "p1",
                "type", ContentEventTypes.POST_PUBLISHED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "content-service",
                "payload", Map.of(
                        "postId", 100,
                        "userId", 1,
                        "title", "t",
                        "content", "c"
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k1", payload));
        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 1L, "k1", payload));

        assertThat(scoreByUserId.get(1)).isEqualTo(10);
    }

    @Test
    void likeCreatedShouldAddPointsToEntityUserId() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "l1",
                "type", SocialEventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));
        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 1L, "k1", payload));

        assertThat(scoreByUserId.get(1)).isEqualTo(1);
    }

    @Test
    void selfLikeShouldNotAddPoints() throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "l2",
                "type", SocialEventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 1,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", payload));

        assertThat(scoreByUserId.getOrDefault(1, 0)).isEqualTo(0);
    }

    @Test
    void likeRemovedShouldSubtractPointsToEntityUserId() throws Exception {
        String created = objectMapper.writeValueAsString(Map.of(
                "eventId", "l3",
                "type", SocialEventTypes.LIKE_CREATED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));
        String removed = objectMapper.writeValueAsString(Map.of(
                "eventId", "l4",
                "type", SocialEventTypes.LIKE_REMOVED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "social-service",
                "payload", Map.of(
                        "actorUserId", 2,
                        "entityType", 1,
                        "entityId", 100,
                        "entityUserId", 1,
                        "postId", 100,
                        "createTime", Instant.now().toString()
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 0L, "k1", created));
        assertThat(scoreByUserId.get(1)).isEqualTo(1);

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 1L, "k1", removed));
        consumer.handleRecord(new ConsumerRecord<>(EventTopics.SOCIAL_EVENTS_V1, 0, 2L, "k1", removed));
        assertThat(scoreByUserId.get(1)).isEqualTo(0);
    }
}
