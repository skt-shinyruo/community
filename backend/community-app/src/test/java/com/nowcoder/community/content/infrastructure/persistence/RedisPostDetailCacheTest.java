package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.result.PostDetailResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPostDetailCacheTest {

    @Test
    void putShouldUseJitteredTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        CacheTtlPolicy ttlPolicy = mock(CacheTtlPolicy.class);
        ContentHotPathProperties properties = new ContentHotPathProperties();
        UUID postId = uuid(10);
        PostDetailResult detail = detail(postId);
        String key = "post:detail:" + postId;

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(jsonCodec.toJson(detail)).thenReturn("{\"id\":\"" + postId + "\"}");
        when(ttlPolicy.jitteredTtl(key, properties.getCache().detailTtl())).thenReturn(Duration.ofSeconds(333));

        RedisPostDetailCache cache = new RedisPostDetailCache(redisTemplate, jsonCodec, ttlPolicy, properties);

        cache.put(postId, detail);

        verify(valueOps).set(key, "{\"id\":\"" + postId + "\"}", Duration.ofSeconds(333));
    }

    @Test
    void getShouldReturnNullAndDeleteMalformedJson() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        UUID postId = uuid(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("post:detail:" + postId)).thenReturn("{bad");
        when(jsonCodec.fromJson("{bad", PostDetailResult.class))
                .thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));

        RedisPostDetailCache cache = new RedisPostDetailCache(redisTemplate, jsonCodec);

        assertThat(cache.get(postId)).isNull();
        verify(redisTemplate).delete("post:detail:" + postId);
    }

    @Test
    void getShouldReturnNullWhenMalformedJsonCleanupFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        UUID postId = uuid(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("post:detail:" + postId)).thenReturn("{bad");
        when(jsonCodec.fromJson("{bad", PostDetailResult.class))
                .thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));
        doThrow(new RuntimeException("redis unavailable")).when(redisTemplate).delete("post:detail:" + postId);

        RedisPostDetailCache cache = new RedisPostDetailCache(redisTemplate, jsonCodec);

        assertThat(cache.get(postId)).isNull();
    }

    private static PostDetailResult detail(UUID postId) {
        return new PostDetailResult(
                postId,
                uuid(2),
                "<title>",
                List.of(),
                0,
                0,
                new Date(1_000),
                new Date(2_000),
                0,
                0,
                0.0,
                uuid(3),
                List.of(),
                0L,
                false,
                false
        );
    }
}
