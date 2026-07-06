package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPostSummaryCacheTest {

    @Test
    void getAllShouldDropMalformedJsonAndDeletePoisonKeys() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        UUID postId = uuid(1);
        PostSummaryResult summary = summary(postId);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of("post:summary:" + postId))).thenReturn(List.of("{bad"));
        when(jsonCodec.fromJson("{bad", PostSummaryResult.class))
                .thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));

        RedisPostSummaryCache cache = new RedisPostSummaryCache(redisTemplate, jsonCodec);

        Map<UUID, PostSummaryResult> result = cache.getAll(List.of(postId));

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(eq(java.util.Set.of("post:summary:" + postId)));
    }

    @Test
    void getAllShouldReturnEmptyWhenMalformedJsonCleanupFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        UUID postId = uuid(1);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.multiGet(List.of("post:summary:" + postId))).thenReturn(List.of("{bad"));
        when(jsonCodec.fromJson("{bad", PostSummaryResult.class))
                .thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));
        doThrow(new RuntimeException("redis unavailable")).when(redisTemplate).delete(any(java.util.Collection.class));

        RedisPostSummaryCache cache = new RedisPostSummaryCache(redisTemplate, jsonCodec);

        assertThat(cache.getAll(List.of(postId))).isEmpty();
    }

    private static PostSummaryResult summary(UUID postId) {
        return new PostSummaryResult(
                postId,
                uuid(2),
                "<title>",
                "<preview>",
                0,
                0,
                new Date(1_000),
                0,
                0.0,
                uuid(3),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }
}
