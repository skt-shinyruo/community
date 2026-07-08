package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.content.application.CacheTtlPolicy;
import com.nowcoder.community.content.application.ContentHotPathProperties;
import com.nowcoder.community.content.application.result.CommentPageResult;
import com.nowcoder.community.content.application.result.CommentResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCommentPageCacheTest {

    @Test
    void putRootPageShouldUseJitteredTtlWhenPolicyIsProvided() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        CacheTtlPolicy ttlPolicy = mock(CacheTtlPolicy.class);
        ContentHotPathProperties properties = new ContentHotPathProperties();
        UUID postId = uuid(100);
        CommentPageResult page = new CommentPageResult(List.of(commentResult(postId)), "next");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(ttlPolicy.jitteredTtl(pageKey(postId, 10), properties.getCache().commentPageTtl()))
                .thenReturn(Duration.ofSeconds(177));

        RedisCommentPageCache cache = new RedisCommentPageCache(redisTemplate, jsonCodec, ttlPolicy, properties);

        cache.putRootPage(postId, "", 10, page);

        verify(valueOps).set(eq(pageKey(postId, 10)), anyString(), eq(Duration.ofSeconds(177)));
        verify(redisTemplate).expire(indexKey(postId), Duration.ofSeconds(177));
    }

    @Test
    void getRootPageShouldReturnCachedFirstPage() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID postId = uuid(100);
        CommentPageResult cached = new CommentPageResult(List.of(commentResult(postId)), "next");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(pageKey(postId, 10))).thenReturn(jsonCodec.toJson(cached));

        RedisCommentPageCache cache = new RedisCommentPageCache(redisTemplate, jsonCodec, 15);

        CommentPageResult result = cache.getRootPage(postId, "", 10);

        assertThat(result).isNotNull();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.postId()).isEqualTo(postId);
            assertThat(item.content()).isEqualTo("cached");
        });
        assertThat(result.nextCursor()).isEqualTo("next");
    }

    @Test
    void putRootPageShouldStorePayloadWithTtlAndIndexKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID postId = uuid(100);
        CommentPageResult page = new CommentPageResult(List.of(commentResult(postId)), "next");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);

        RedisCommentPageCache cache = new RedisCommentPageCache(redisTemplate, jsonCodec, 15);

        cache.putRootPage(postId, "", 10, page);

        verify(valueOps).set(eq(pageKey(postId, 10)), anyString(), eq(Duration.ofSeconds(15)));
        verify(setOps).add(indexKey(postId), pageKey(postId, 10));
        verify(redisTemplate).expire(indexKey(postId), Duration.ofSeconds(15));
    }

    @Test
    void getRootPageShouldDeleteMalformedJsonAndReturnNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        UUID postId = uuid(100);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(pageKey(postId, 10))).thenReturn("{bad");
        when(jsonCodec.fromJson("{bad", CommentPageResult.class))
                .thenThrow(new JsonCodecException("boom", new RuntimeException("boom")));

        RedisCommentPageCache cache = new RedisCommentPageCache(redisTemplate, jsonCodec, 15);

        assertThat(cache.getRootPage(postId, "", 10)).isNull();
        verify(redisTemplate).delete(pageKey(postId, 10));
    }

    @Test
    void evictPostShouldDeleteIndexedPageKeysAndIndex() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        SetOperations<String, String> setOps = mock(SetOperations.class);
        JsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
        UUID postId = uuid(100);
        String sizeTenKey = pageKey(postId, 10);
        String sizeTwentyKey = pageKey(postId, 20);

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(indexKey(postId))).thenReturn(new LinkedHashSet<>(List.of(sizeTenKey, sizeTwentyKey)));

        RedisCommentPageCache cache = new RedisCommentPageCache(redisTemplate, jsonCodec, 15);

        cache.evictPost(postId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> keysCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(redisTemplate).delete(keysCaptor.capture());
        assertThat(keysCaptor.getValue()).containsExactlyInAnyOrder(sizeTenKey, sizeTwentyKey, indexKey(postId));
    }

    private static CommentResult commentResult(UUID postId) {
        return new CommentResult(
                uuid(200),
                uuid(7),
                postId,
                uuid(200),
                null,
                null,
                "cached",
                new Date(1_000),
                null,
                0
        );
    }

    private static String pageKey(UUID postId, int size) {
        return "comment:root-page:v2:" + postId + ":cursor:initial:size:" + size;
    }

    private static String indexKey(UUID postId) {
        return "comment:root-page:v2:" + postId + ":keys";
    }
}
