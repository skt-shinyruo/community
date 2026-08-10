package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.PostCounterCache;
import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPostCounterCacheTest {

    @Test
    void getShouldInvalidateCorruptInitializedSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        UUID postId = uuid(5);
        Map<Object, Object> values = new LinkedHashMap<>();
        values.put("initialized", "1");
        values.put("baseViewCount", "bad");
        values.put("baseLikeCount", "7");
        values.put("baseCommentCount", "3");
        values.put("baseBookmarkCount", "2");
        values.put("baseScore", "bad-score");
        String counterKey = counterKey(postId);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        when(hashes.entries(counterKey)).thenReturn(values);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(counterKey)),
                eq("1"),
                eq("baseViewCount"),
                eq("baseScore")
        )).thenReturn(1L);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        PostCounterSnapshot snapshot = cache.get(postId);

        assertThat(snapshot.viewCount()).isZero();
        assertThat(snapshot.likeCount()).isEqualTo(7L);
        assertThat(snapshot.commentCount()).isEqualTo(3L);
        assertThat(snapshot.bookmarkCount()).isEqualTo(2L);
        assertThat(snapshot.score()).isZero();
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(counterKey)),
                eq("1"),
                eq("baseViewCount"),
                eq("baseScore")
        );
    }

    @Test
    void getShouldComposePreInitializationDeltasWithLegacyCounters() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        UUID postId = uuid(9);
        Map<Object, Object> previous = new LinkedHashMap<>();
        previous.put("viewCount", "10");
        previous.put("likeCount", "7");
        previous.put("commentCount", "3");
        previous.put("bookmarkCount", "4");
        previous.put("score", "20.0");
        Map<Object, Object> v2 = new LinkedHashMap<>();
        v2.put("deltaViewCount", "2");
        v2.put("deltaLikeCount", "-1");
        v2.put("bookmarkCountAbsolute", "5");
        v2.put("scoreOverlay", "30.0");
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        when(hashes.entries(counterKey(postId))).thenReturn(v2);
        when(hashes.entries(previousCounterKey(postId))).thenReturn(previous);
        when(hashes.entries(legacyCounterKey(postId))).thenReturn(Map.of());
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        PostCounterSnapshot snapshot = cache.get(postId);

        assertThat(snapshot.viewCount()).isEqualTo(12L);
        assertThat(snapshot.likeCount()).isEqualTo(6L);
        assertThat(snapshot.commentCount()).isEqualTo(3L);
        assertThat(snapshot.bookmarkCount()).isEqualTo(5L);
        assertThat(snapshot.score()).isEqualTo(30.0);
    }

    @Test
    void viewUpdateShouldInitializeDeduplicateIncrementAndMarkDirtyInOneClusterSlot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(6);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);
        PostCounterSnapshot baseline = new PostCounterSnapshot(postId, 9L, 2L, 3L, 4L, 8.5, 12L);

        cache.recordView(postId, "viewer:private-value", Instant.EPOCH, baseline);

        List<String> keys = List.of(
                counterKey(postId),
                viewerKeyPrefix(postId),
                dirtyKey(postId),
                dirtySequenceKey(postId)
        );
        int slot = ClusterSlotHashUtil.calculateSlot(keys.get(0));
        assertThat(keys).allSatisfy(key -> assertThat(ClusterSlotHashUtil.calculateSlot(key)).isEqualTo(slot));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.argThat(actual -> actual.size() == 4
                        && actual.get(0).equals(counterKey(postId))
                        && actual.get(1).startsWith(viewerKeyPrefix(postId))
                        && actual.get(2).equals(dirtyKey(postId))
                        && actual.get(3).equals(dirtySequenceKey(postId))),
                eq("9"),
                eq("2"),
                eq("3"),
                eq("4"),
                eq("8.5"),
                eq("12"),
                eq("0"),
                eq("86400000"),
                eq(postId.toString())
        );
    }

    @Test
    void counterQueuesShouldBeShardedAcrossClusterSlots() {
        UUID first = postIdInShard(0);
        UUID second = postIdInShard(1);

        assertThat(ClusterSlotHashUtil.calculateSlot(counterKey(first)))
                .isEqualTo(ClusterSlotHashUtil.calculateSlot(dirtyKey(first)));
        assertThat(ClusterSlotHashUtil.calculateSlot(counterKey(second)))
                .isEqualTo(ClusterSlotHashUtil.calculateSlot(dirtyKey(second)));
        assertThat(ClusterSlotHashUtil.calculateSlot(counterKey(first)))
                .isNotEqualTo(ClusterSlotHashUtil.calculateSlot(counterKey(second)));
    }

    @Test
    void canonicalCounterChangeShouldOnlyAllocateADirtyRevision() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(12);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(dirtyKey(postId), dirtySequenceKey(postId))),
                eq(postId.toString())
        )).thenReturn(17L);

        cache.markDirty(postId);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(dirtyKey(postId), dirtySequenceKey(postId))),
                eq(postId.toString())
        );
    }

    @Test
    void initializationShouldCarryPersistentRevisionIntoTheClusterSlotSequence() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UUID postId = uuid(13);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        cache.initializeIfAbsent(new PostCounterSnapshot(postId, 9L, 2L, 3L, 4L, 8.5, 21L));

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(counterKey(postId), dirtyKey(postId), dirtySequenceKey(postId))),
                eq("9"),
                eq("2"),
                eq("3"),
                eq("4"),
                eq("8.5"),
                eq("21"),
                eq(postId.toString())
        );
    }

    @Test
    void dirtyPostShouldCarryQueueIdentityForFencedClear() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        UUID postId = postIdInShard(0);
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(redisTemplate.opsForZSet()).thenReturn(zsets);
        when(zsets.rangeWithScores("post:counter:dirty", 0L, 0L)).thenReturn(new LinkedHashSet<>());
        when(zsets.rangeWithScores(dirtyKey(postId), 0L, 0L)).thenReturn(new LinkedHashSet<>(List.of(tuple)));
        when(tuple.getValue()).thenReturn(postId.toString());
        when(tuple.getScore()).thenReturn(11.0);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        List<PostCounterCache.DirtyPost> dirtyPosts = cache.dirtyPosts(1);
        cache.clearDirtyPosts(dirtyPosts);

        assertThat(dirtyPosts).containsExactly(new PostCounterCache.DirtyPost(postId, 11L, "shard-0"));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(dirtyKey(postId))),
                eq(postId.toString()),
                eq("11")
        );
    }

    @Test
    void dirtyScanShouldRedistributeUnusedShardBudgetToAHotShard() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        List<UUID> postIds = postIdsInShard(0, 5);
        List<ZSetOperations.TypedTuple<String>> tuples = List.of(
                tuple(postIds.get(0), 1.0),
                tuple(postIds.get(1), 2.0),
                tuple(postIds.get(2), 3.0),
                tuple(postIds.get(3), 4.0),
                tuple(postIds.get(4), 5.0)
        );
        String key = dirtyKey(postIds.get(0));
        when(redisTemplate.opsForZSet()).thenReturn(zsets);
        when(zsets.rangeWithScores("post:counter:dirty", 0L, 0L)).thenReturn(new LinkedHashSet<>());
        when(zsets.rangeWithScores(key, 0L, 0L))
                .thenReturn(new LinkedHashSet<>(tuples.subList(0, 1)));
        when(zsets.rangeWithScores(key, 0L, 3L))
                .thenReturn(new LinkedHashSet<>(tuples.subList(0, 4)));
        when(zsets.rangeWithScores(key, 0L, 4L))
                .thenReturn(new LinkedHashSet<>(tuples));
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        List<PostCounterCache.DirtyPost> result = cache.dirtyPosts(5);

        assertThat(result).extracting(PostCounterCache.DirtyPost::postId).containsExactlyElementsOf(postIds);
        assertThat(result).extracting(PostCounterCache.DirtyPost::queueId).containsOnly("shard-0");
        verify(zsets).rangeWithScores(key, 0L, 4L);
    }

    @Test
    void dirtyScanShouldGiveActiveShardsAFairFirstPassBeforeRedistribution() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        List<UUID> firstShardPosts = postIdsInShard(0, 2);
        List<UUID> secondShardPosts = postIdsInShard(1, 2);
        List<ZSetOperations.TypedTuple<String>> firstTuples = List.of(
                tuple(firstShardPosts.get(0), 1.0),
                tuple(firstShardPosts.get(1), 2.0)
        );
        List<ZSetOperations.TypedTuple<String>> secondTuples = List.of(
                tuple(secondShardPosts.get(0), 1.0),
                tuple(secondShardPosts.get(1), 2.0)
        );
        when(redisTemplate.opsForZSet()).thenReturn(zsets);
        when(zsets.rangeWithScores("post:counter:dirty", 0L, 0L)).thenReturn(new LinkedHashSet<>());
        stubGrowingQueue(zsets, dirtyKey(firstShardPosts.get(0)), firstTuples);
        stubGrowingQueue(zsets, dirtyKey(secondShardPosts.get(0)), secondTuples);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        List<PostCounterCache.DirtyPost> result = cache.dirtyPosts(4);

        assertThat(result).extracting(PostCounterCache.DirtyPost::postId).containsExactly(
                firstShardPosts.get(0),
                secondShardPosts.get(0),
                firstShardPosts.get(1),
                secondShardPosts.get(1)
        );
    }

    @Test
    void dirtyScanShouldRemovePoisonMembersAndRefillTheRequestedPage() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        List<UUID> validPosts = postIdsInShard(0, 3);
        UUID invalidScorePost = validPosts.get(0);
        UUID firstValidPost = validPosts.get(1);
        UUID secondValidPost = validPosts.get(2);
        UUID wrongShardPost = postIdInShard(1);
        String key = dirtyKey(firstValidPost);
        ZSetOperations.TypedTuple<String> invalidUuid = tuple("not-a-uuid", 1.0);
        ZSetOperations.TypedTuple<String> wrongShard = tuple(wrongShardPost, 1.0);
        ZSetOperations.TypedTuple<String> invalidScore = tuple(invalidScorePost, 0.0);
        ZSetOperations.TypedTuple<String> firstValid = tuple(firstValidPost, 2.0);
        ZSetOperations.TypedTuple<String> secondValid = tuple(secondValidPost, 3.0);
        when(redisTemplate.opsForZSet()).thenReturn(zsets);
        when(zsets.rangeWithScores("post:counter:dirty", 0L, 0L)).thenReturn(new LinkedHashSet<>());
        when(zsets.rangeWithScores(key, 0L, 0L)).thenReturn(
                new LinkedHashSet<>(List.of(invalidUuid)),
                new LinkedHashSet<>(List.of(wrongShard)),
                new LinkedHashSet<>(List.of(invalidScore)),
                new LinkedHashSet<>(List.of(firstValid)),
                new LinkedHashSet<>(List.of(firstValid))
        );
        when(zsets.rangeWithScores(key, 0L, 1L))
                .thenReturn(new LinkedHashSet<>(List.of(firstValid, secondValid)));
        when(zsets.remove(key, "not-a-uuid")).thenReturn(1L);
        when(zsets.remove(key, wrongShardPost.toString())).thenReturn(1L);
        when(zsets.remove(key, invalidScorePost.toString())).thenReturn(1L);
        RedisPostCounterCache cache = newCache(redisTemplate, 86_400L);

        List<PostCounterCache.DirtyPost> result = cache.dirtyPosts(2);

        assertThat(result).extracting(PostCounterCache.DirtyPost::postId)
                .containsExactly(firstValidPost, secondValidPost);
        verify(zsets).remove(key, "not-a-uuid");
        verify(zsets).remove(key, wrongShardPost.toString());
        verify(zsets).remove(key, invalidScorePost.toString());
    }

    private static void stubGrowingQueue(
            ZSetOperations<String, String> zsets,
            String key,
            List<ZSetOperations.TypedTuple<String>> tuples
    ) {
        when(zsets.rangeWithScores(key, 0L, 0L))
                .thenReturn(new LinkedHashSet<>(tuples.subList(0, 1)));
        when(zsets.rangeWithScores(key, 0L, 1L))
                .thenReturn(new LinkedHashSet<>(tuples));
    }

    private static ZSetOperations.TypedTuple<String> tuple(UUID postId, double score) {
        return tuple(postId.toString(), score);
    }

    @SuppressWarnings("unchecked")
    private static ZSetOperations.TypedTuple<String> tuple(String member, double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(member);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }

    private static List<UUID> postIdsInShard(int wantedShard, int count) {
        java.util.ArrayList<UUID> result = new java.util.ArrayList<>(count);
        for (long value = 1L; value < 100_000L && result.size() < count; value++) {
            UUID candidate = new UUID(0L, value);
            if (shard(candidate) == wantedShard) {
                result.add(candidate);
            }
        }
        if (result.size() != count) {
            throw new AssertionError("unable to find test UUIDs for shard " + wantedShard);
        }
        return List.copyOf(result);
    }

    private static UUID postIdInShard(int wantedShard) {
        for (long value = 1L; value < 10_000L; value++) {
            UUID candidate = new UUID(0L, value);
            if (shard(candidate) == wantedShard) {
                return candidate;
            }
        }
        throw new AssertionError("unable to find test UUID for shard " + wantedShard);
    }

    private static RedisPostCounterCache newCache(
            StringRedisTemplate redisTemplate,
            long viewerWindowSeconds
    ) {
        return new RedisPostCounterCache(
                redisTemplate,
                viewerWindowSeconds,
                java.time.Clock.systemUTC()
        );
    }

    private static int shard(UUID postId) {
        return Math.floorMod(postId.hashCode(), 32);
    }

    private static String tag(UUID postId) {
        String value = Integer.toHexString(shard(postId));
        return "{post-counter-" + (value.length() == 1 ? "0" + value : value) + "}";
    }

    private static String counterKey(UUID postId) {
        return "post:counter:v2:" + tag(postId) + ":" + postId;
    }

    private static String dirtyKey(UUID postId) {
        return "post:counter:v2:" + tag(postId) + ":dirty";
    }

    private static String dirtySequenceKey(UUID postId) {
        return "post:counter:v2:" + tag(postId) + ":sequence";
    }

    private static String viewerKeyPrefix(UUID postId) {
        return "post:viewer:v2:" + tag(postId) + ":" + postId + ":";
    }

    private static String previousCounterKey(UUID postId) {
        return "post:counter:{post:counter:dirty}:" + postId;
    }

    private static String legacyCounterKey(UUID postId) {
        return "post:counter:" + postId;
    }
}
