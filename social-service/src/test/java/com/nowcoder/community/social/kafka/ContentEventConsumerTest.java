package com.nowcoder.community.social.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.domain.EntityTypes;
import com.nowcoder.community.common.event.EventTopics;
import com.nowcoder.community.content.api.event.ContentEventTypes;
import com.nowcoder.community.social.projection.ContentEntityProjectionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

class ContentEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void postPublishedShouldUpsertProjection() throws Exception {
        ContentEntityProjectionRepository repo = Mockito.mock(ContentEntityProjectionRepository.class);
        ContentEventConsumer consumer = new ContentEventConsumer(
                objectMapper,
                repo,
                new SimpleMeterRegistry(),
                "SKIP",
                "DLQ"
        );

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-post-1",
                "type", ContentEventTypes.POST_PUBLISHED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "content-service",
                "payload", Map.of(
                        "postId", 123,
                        "userId", 7,
                        "status", 0
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k1", payload));

        Mockito.verify(repo, times(1)).upsertIfNewer(
                eq(EntityTypes.POST),
                eq(123L),
                eq(7L),
                eq(123L),
                eq(0),
                any()
        );
    }

    @Test
    void commentDeletedShouldUpsertProjectionWithDeletedStatus() throws Exception {
        ContentEntityProjectionRepository repo = Mockito.mock(ContentEntityProjectionRepository.class);
        ContentEventConsumer consumer = new ContentEventConsumer(
                objectMapper,
                repo,
                new SimpleMeterRegistry(),
                "SKIP",
                "DLQ"
        );

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-comment-1",
                "type", ContentEventTypes.COMMENT_DELETED,
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "content-service",
                "payload", Map.of(
                        "commentId", 55,
                        "postId", 123,
                        "userId", 9
                )
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.COMMENT_EVENTS_V1, 0, 0L, "k2", payload));

        Mockito.verify(repo, times(1)).upsertIfNewer(
                eq(EntityTypes.COMMENT),
                eq(55L),
                eq(9L),
                eq(123L),
                eq(1),
                any()
        );
    }

    @Test
    void unknownTypeShouldBeSkippedWhenConfiguredAsSkip() throws Exception {
        ContentEntityProjectionRepository repo = Mockito.mock(ContentEntityProjectionRepository.class);
        ContentEventConsumer consumer = new ContentEventConsumer(
                objectMapper,
                repo,
                new SimpleMeterRegistry(),
                "SKIP",
                "DLQ"
        );

        String payload = objectMapper.writeValueAsString(Map.of(
                "eventId", "e-unknown-1",
                "type", "SomethingElse",
                "version", 1,
                "occurredAt", Instant.now().toString(),
                "producer", "content-service",
                "payload", Map.of()
        ));

        consumer.handleRecord(new ConsumerRecord<>(EventTopics.POST_EVENTS_V1, 0, 0L, "k3", payload));

        Mockito.verify(repo, times(0)).upsertIfNewer(anyInt(), anyLong(), anyLong(), anyLong(), anyInt(), any());
    }
}
