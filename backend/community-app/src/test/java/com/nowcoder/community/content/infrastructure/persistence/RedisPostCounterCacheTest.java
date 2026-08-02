package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPostCounterCacheTest {

    @Test
    void getShouldDeleteInvalidNumericFieldsAndReturnSafeSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        UUID postId = uuid(5);
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put("viewCount", "bad");
        values.put("likeCount", "7");
        values.put("commentCount", "3");
        values.put("bookmarkCount", "2");
        values.put("score", "bad-score");
        String counterKey = counterKey(postId);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(counterKey)).thenReturn(values);
        when(hashOperations.entries(legacyCounterKey(postId))).thenReturn(Map.of());

        RedisPostCounterCache cache = new RedisPostCounterCache(redisTemplate, 86_400L);

        PostCounterSnapshot snapshot = cache.get(postId);

        assertThat(snapshot.viewCount()).isZero();
        assertThat(snapshot.likeCount()).isEqualTo(7L);
        assertThat(snapshot.commentCount()).isEqualTo(3L);
        assertThat(snapshot.bookmarkCount()).isEqualTo(2L);
        assertThat(snapshot.score()).isZero();
        verify(hashOperations).delete(counterKey, "viewCount", "score");
    }

    @Test
    void getShouldComposeLegacyBaselineWithClusterSafeCounterOverlay() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        UUID postId = uuid(9);
        Map<Object, Object> legacyValues = new LinkedHashMap<>();
        legacyValues.put("viewCount", "10");
        legacyValues.put("likeCount", "7");
        legacyValues.put("commentCount", "3");
        legacyValues.put("bookmarkCount", "4");
        legacyValues.put("score", "20.0");
        Map<Object, Object> overlayValues = new LinkedHashMap<>();
        overlayValues.put("viewCount", "2");
        overlayValues.put("likeCount", "-1");
        overlayValues.put("bookmarkCount", "1");
        overlayValues.put("score", "30.0");
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(counterKey(postId))).thenReturn(overlayValues);
        when(hashOperations.entries(legacyCounterKey(postId))).thenReturn(legacyValues);
        RedisPostCounterCache cache = new RedisPostCounterCache(redisTemplate, 86_400L);

        PostCounterSnapshot snapshot = cache.get(postId);

        assertThat(snapshot.viewCount()).isEqualTo(12L);
        assertThat(snapshot.likeCount()).isEqualTo(6L);
        assertThat(snapshot.commentCount()).isEqualTo(3L);
        assertThat(snapshot.bookmarkCount()).isEqualTo(5L);
        assertThat(snapshot.score()).isEqualTo(30.0);
    }

    @Test
    void counterAndDirtyKeysShouldShareClusterSlotForAtomicUpdates() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(6);
        String counterKey = counterKey(postId);

        RedisPostCounterCache cache = new RedisPostCounterCache(redisTemplate, 86_400L);

        cache.incrementLikeCount(postId, 2L);
        cache.updateScore(postId, 42.5);

        assertThat(ClusterSlotHashUtil.calculateSlot(counterKey))
                .isEqualTo(ClusterSlotHashUtil.calculateSlot(dirtyKey()));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(counterKey, dirtyKey(), dirtySequenceKey())),
                eq("likeCount"),
                eq("2"),
                eq(postId.toString())
        );
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(counterKey, dirtyKey(), dirtySequenceKey())),
                eq("score"),
                eq("42.5"),
                eq(postId.toString())
        );
    }

    @Test
    void dirtyPostScanAndClearShouldKeepUsingOneRemovableSortedSet() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        UUID first = uuid(7);
        UUID second = uuid(8);
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> firstTuple = mock(ZSetOperations.TypedTuple.class);
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> secondTuple = mock(ZSetOperations.TypedTuple.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(firstTuple.getValue()).thenReturn(first.toString());
        when(firstTuple.getScore()).thenReturn(11.0);
        when(secondTuple.getValue()).thenReturn(second.toString());
        when(secondTuple.getScore()).thenReturn(12.0);
        when(zSetOperations.rangeWithScores(dirtyKey(), 0L, 1L))
                .thenReturn(new LinkedHashSet<>(List.of(firstTuple, secondTuple)));
        RedisPostCounterCache cache = new RedisPostCounterCache(redisTemplate, 86_400L);

        List<com.nowcoder.community.content.application.PostCounterCache.DirtyPost> dirtyPosts =
                cache.dirtyPosts(2);
        cache.clearDirtyPosts(dirtyPosts);

        assertThat(dirtyPosts).containsExactly(
                new com.nowcoder.community.content.application.PostCounterCache.DirtyPost(first, 11L),
                new com.nowcoder.community.content.application.PostCounterCache.DirtyPost(second, 12L)
        );
        verify(zSetOperations).rangeWithScores(dirtyKey(), 0L, 1L);
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(dirtyKey())),
                eq(first.toString()),
                eq("11"),
                eq(second.toString()),
                eq("12")
        );
    }

    private static String counterKey(UUID postId) {
        return "post:counter:{post:counter:dirty}:" + postId;
    }

    private static String dirtyKey() {
        return "post:counter:dirty";
    }

    private static String dirtySequenceKey() {
        return "post:counter:{post:counter:dirty}:sequence";
    }

    private static String legacyCounterKey(UUID postId) {
        return "post:counter:" + postId;
    }
}
