package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.FollowFeedCache;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisFollowFeedCacheTest {

    @Test
    void getOrLoadPageShouldUseJitteredTtlWhenPolicyIsProvided() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        CacheTtlPolicy ttlPolicy = mock(CacheTtlPolicy.class);
        ContentHotPathProperties properties = new ContentHotPathProperties();
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String key = "post:feed:follow:" + viewerId + ":cursor:initial:size:20";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);
        when(ttlPolicy.jitteredTtl(key, properties.getCache().followPageTtl()))
                .thenReturn(Duration.ofSeconds(144));

        FollowFeedCache cache = new RedisFollowFeedCache(redisTemplate, jsonCodec, ttlPolicy, properties);

        cache.getOrLoadPage(viewerId, "", 20, () -> new FollowFeedCache.FollowFeedPageSlice(List.of(postId), null, null));

        verify(valueOperations).set(eq(key), anyString(), eq(Duration.ofSeconds(144)));
    }

    @Test
    void getOrLoadPageShouldKeepDefaultConstructorTtlDeterministic() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID viewerId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        UUID postId = UUID.randomUUID();
        String key = "post:feed:follow:" + viewerId + ":cursor:cursor-token:size:2";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn(null);

        FollowFeedCache cache = new RedisFollowFeedCache(redisTemplate, jsonCodec);

        cache.getOrLoadPage(viewerId, "cursor-token", 2, () -> new FollowFeedCache.FollowFeedPageSlice(List.of(postId), null, null));

        verify(valueOperations).set(eq(key), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void getOrLoadPageShouldCacheLoadedIdsInRedisWithTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID viewerId = UUID.randomUUID();
        UUID firstPostId = UUID.randomUUID();
        UUID secondPostId = UUID.randomUUID();
        Date anchorTime = new Date(1_000L);
        FollowFeedCache.FollowFeedPageSlice cachedPage = new FollowFeedCache.FollowFeedPageSlice(
                List.of(firstPostId, secondPostId),
                anchorTime,
                secondPostId
        );
        AtomicInteger loads = new AtomicInteger();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:feed:follow:" + viewerId + ":cursor:cursor-token:size:2")).thenReturn(null);

        FollowFeedCache cache = new RedisFollowFeedCache(redisTemplate, jsonCodec);

        FollowFeedCache.FollowFeedPageSlice result = cache.getOrLoadPage(viewerId, "cursor-token", 2, () -> {
            loads.incrementAndGet();
            return cachedPage;
        });

        assertThat(result.ids()).containsExactly(firstPostId, secondPostId);
        assertThat(result.anchorCreateTime()).isEqualTo(anchorTime);
        assertThat(result.anchorPostId()).isEqualTo(secondPostId);
        assertThat(loads).hasValue(1);
        verify(valueOperations).set(eq("post:feed:follow:" + viewerId + ":cursor:cursor-token:size:2"), anyString(), any(Duration.class));
    }

    @Test
    void getOrLoadPageShouldTreatCachedEmptyPageAsCacheHit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID viewerId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:feed:follow:" + viewerId + ":cursor:initial:size:20"))
                .thenReturn("{\"ids\":[]}");

        FollowFeedCache cache = new RedisFollowFeedCache(redisTemplate, jsonCodec);

        FollowFeedCache.FollowFeedPageSlice result = cache.getOrLoadPage(viewerId, "", 20, () -> {
            loads.incrementAndGet();
            return new FollowFeedCache.FollowFeedPageSlice(List.of(UUID.randomUUID()), null, null);
        });

        assertThat(result.ids()).isEmpty();
        assertThat(loads).hasValue(0);
        verify(valueOperations, never()).set(eq("post:feed:follow:" + viewerId + ":cursor:initial:size:20"), anyString(), any(Duration.class));
    }
}
