package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.application.HotFeedProjectionGuard;
import com.nowcoder.community.content.application.result.PostDetailResult;
import com.nowcoder.community.content.application.result.PostSummaryResult;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import io.lettuce.core.cluster.SlotHash;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class RedisPostProjectionTerminalFencingIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final long TERMINAL_FENCE_TTL_SECONDS = TimeUnit.DAYS.toSeconds(7);
    private static final long TERMINAL_FENCE_TTL_TOLERANCE_SECONDS = 10L;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void terminalSinkFencesShouldDefeatExpiredCurrentWriterInEitherOrdering() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID payloadBoardId = UUID.randomUUID();
        UUID firstCategoryId = UUID.randomUUID();
        UUID secondCategoryId = UUID.randomUUID();
        List<UUID> boardIds = List.of(payloadBoardId, firstCategoryId, secondCategoryId);
        CategoryContentRepository categoryRepository = mock(CategoryContentRepository.class);
        when(categoryRepository.listCategories()).thenReturn(List.of(
                category(firstCategoryId),
                category(secondCategoryId)
        ));
        RedisPostFeedCache feedCache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryRepository
        );
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        RedisPostDetailCache detailCache = new RedisPostDetailCache(redisTemplate, jsonCodec);
        RedisHotFeedProjectionGuard guard = new RedisHotFeedProjectionGuard(redisTemplate);
        try {
            UUID lateWriterPostId = UUID.randomUUID();
            HotFeedProjectionGuard.ProjectionAttempt lateWriter = guard.tryBegin(
                    lateWriterPostId,
                    "evt-normal-late",
                    10L,
                    false
            );
            assertThat(lateWriter.accepted()).isTrue();
            assertThat(guard.isCurrent(lateWriter)).isTrue();
            assertThat(redisTemplate.delete(lockKey(lateWriterPostId))).isTrue();

            commitTerminalDeletion(
                    guard,
                    feedCache,
                    summaryCache,
                    detailCache,
                    lateWriterPostId,
                    payloadBoardId,
                    "evt-delete-before-late-writes"
            );

            writeEverySink(feedCache, summaryCache, detailCache, lateWriterPostId, boardIds);

            assertAbsentFromEverySink(redisTemplate, summaryCache, detailCache, lateWriterPostId, boardIds);
            assertThatThrownBy(() -> guard.commit(lateWriter))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hot feed projection commit lost lease");
            assertBoundedPerPostFences(redisTemplate, lateWriterPostId, boardIds);
            assertAllSinkPairsShareSlots(lateWriterPostId, boardIds);

            UUID earlyWriterPostId = UUID.randomUUID();
            HotFeedProjectionGuard.ProjectionAttempt earlyWriter = guard.tryBegin(
                    earlyWriterPostId,
                    "evt-normal-early",
                    10L,
                    false
            );
            assertThat(earlyWriter.accepted()).isTrue();
            assertThat(guard.isCurrent(earlyWriter)).isTrue();
            writeEverySink(feedCache, summaryCache, detailCache, earlyWriterPostId, boardIds);
            assertThat(redisTemplate.delete(lockKey(earlyWriterPostId))).isTrue();

            commitTerminalDeletion(
                    guard,
                    feedCache,
                    summaryCache,
                    detailCache,
                    earlyWriterPostId,
                    payloadBoardId,
                    "evt-delete-after-early-writes"
            );

            assertAbsentFromEverySink(redisTemplate, summaryCache, detailCache, earlyWriterPostId, boardIds);
            assertThatThrownBy(() -> guard.commit(earlyWriter))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hot feed projection commit lost lease");
            assertBoundedPerPostFences(redisTemplate, earlyWriterPostId, boardIds);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void ordinaryEvictionShouldNotCreateAnyTerminalSinkFence() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID postId = UUID.randomUUID();
        UUID payloadBoardId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        CategoryContentRepository categoryRepository = mock(CategoryContentRepository.class);
        when(categoryRepository.listCategories()).thenReturn(List.of(category(categoryId)));
        RedisPostFeedCache feedCache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryRepository
        );
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        RedisPostDetailCache detailCache = new RedisPostDetailCache(redisTemplate, jsonCodec);
        try {
            feedCache.remove(postId, null);
            summaryCache.evictAll(List.of(postId));
            detailCache.evict(postId);

            assertThat(redisTemplate.hasKey(feedFenceKey(globalFeedKey(), postId))).isFalse();
            assertThat(redisTemplate.hasKey(feedFenceKey(boardFeedKey(payloadBoardId), postId))).isFalse();
            assertThat(redisTemplate.hasKey(feedFenceKey(boardFeedKey(categoryId), postId))).isFalse();
            assertThat(redisTemplate.hasKey(summaryFenceKey(postId))).isFalse();
            assertThat(redisTemplate.hasKey(detailFenceKey(postId))).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void aggregateVersionFenceShouldRejectAReadThatFinishesAfterNewerInvalidation() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID postId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        CategoryContentRepository categoryRepository = mock(CategoryContentRepository.class);
        when(categoryRepository.listCategories()).thenReturn(List.of(category(boardId)));
        RedisPostFeedCache feedCache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryRepository
        );
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        RedisPostDetailCache detailCache = new RedisPostDetailCache(redisTemplate, jsonCodec);
        try {
            feedCache.remove(postId, null, 11L);
            summaryCache.evictAll(List.of(postId), 11L);
            detailCache.evict(postId, 11L);

            feedCache.upsertGlobalHot(postId, 99.0, "hot-v2", 10L);
            feedCache.upsertBoardHot(boardId, postId, 98.0, "hot-v2", 10L);
            summaryCache.putVersioned(List.of(new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                    summary(postId),
                    10L
            )));
            detailCache.put(postId, detail(postId), 10L);

            assertAbsentFromEverySink(redisTemplate, summaryCache, detailCache, postId, List.of(boardId));
            assertBoundedVersionMarker(redisTemplate, feedVersionKey(globalFeedKey(), postId), 11L);
            assertBoundedVersionMarker(redisTemplate, feedVersionKey(boardFeedKey(boardId), postId), 11L);
            assertBoundedVersionMarker(redisTemplate, summaryVersionKey(postId), 11L);
            assertBoundedVersionMarker(redisTemplate, summaryScoreVersionKey(postId), 0L);
            assertBoundedVersionMarker(redisTemplate, detailVersionKey(postId), 11L);

            feedCache.upsertGlobalHot(postId, 99.0, "hot-v2", 11L);
            feedCache.upsertBoardHot(boardId, postId, 98.0, "hot-v2", 11L);
            summaryCache.putVersioned(List.of(new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                    summary(postId),
                    11L
            )));
            detailCache.put(postId, detail(postId), 11L);

            assertThat(redisTemplate.opsForZSet().score(globalFeedKey(), postId.toString())).isEqualTo(99.0);
            assertThat(redisTemplate.opsForZSet().score(boardFeedKey(boardId), postId.toString())).isEqualTo(98.0);
            assertThat(summaryCache.getAll(List.of(postId))).containsKey(postId);
            assertThat(detailCache.get(postId)).isNotNull();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void feedScoreShouldAdvanceLexicographicallyByAggregateAndScoreVersion() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID postId = UUID.randomUUID();
        RedisPostFeedCache feedCache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                mock(CategoryContentRepository.class)
        );
        try {
            feedCache.upsertGlobalHot(postId, 10.0, "hot-v2", 7L, 3L);
            feedCache.upsertGlobalHot(postId, 99.0, "hot-v2", 7L, 2L);

            assertThat(redisTemplate.opsForZSet().score(globalFeedKey(), postId.toString())).isEqualTo(10.0);

            feedCache.upsertGlobalHot(postId, 20.0, "hot-v2", 8L, 1L);
            feedCache.upsertGlobalHot(postId, 88.0, "hot-v2", 7L, 4L);

            assertThat(redisTemplate.opsForZSet().score(globalFeedKey(), postId.toString())).isEqualTo(20.0);

            feedCache.upsertGlobalHot(postId, 30.0, "hot-v2", 8L, 2L);

            assertThat(redisTemplate.opsForZSet().score(globalFeedKey(), postId.toString())).isEqualTo(30.0);
            assertThat(redisTemplate.opsForValue().get(feedVersionKey(globalFeedKey(), postId))).isEqualTo("8");
            assertThat(redisTemplate.opsForValue().get(feedScoreVersionKey(globalFeedKey(), postId)))
                    .isEqualTo("2");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void summaryScoreShouldAdvanceLexicographicallyByAggregateAndScoreVersion() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID postId = UUID.randomUUID();
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        try {
            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 10.0),
                            7L,
                            3L
                    )
            ));
            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 99.0),
                            7L,
                            2L
                    )
            ));

            assertThat(summaryCache.getAll(List.of(postId)).get(postId).score()).isEqualTo(10.0);

            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 20.0),
                            8L,
                            1L
                    )
            ));
            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 88.0),
                            7L,
                            4L
                    )
            ));

            assertThat(summaryCache.getAll(List.of(postId)).get(postId).score()).isEqualTo(20.0);

            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 30.0),
                            8L,
                            2L
                    )
            ));

            assertThat(summaryCache.getAll(List.of(postId)).get(postId).score()).isEqualTo(30.0);
            assertThat(redisTemplate.opsForValue().get(summaryVersionKey(postId))).isEqualTo("8");
            assertThat(redisTemplate.opsForValue().get(summaryScoreVersionKey(postId))).isEqualTo("2");
            assertPairSharesSlot(summaryKey(postId), summaryScoreVersionKey(postId));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void summaryScoreEvictionShouldRejectAReadThatFinishesWithThePreviousScoreVersion() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redisTemplate = redisTemplate(connectionFactory);
        UUID postId = UUID.randomUUID();
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        try {
            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 10.0),
                            7L,
                            3L
                    )
            ));

            summaryCache.evictAll(List.of(postId), 7L, 4L);
            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 99.0),
                            7L,
                            3L
                    )
            ));

            assertThat(summaryCache.getAll(List.of(postId))).isEmpty();
            assertBoundedVersionMarker(redisTemplate, summaryVersionKey(postId), 7L);
            assertBoundedVersionMarker(redisTemplate, summaryScoreVersionKey(postId), 4L);

            summaryCache.putVersioned(List.of(
                    new com.nowcoder.community.content.application.PostSummaryCache.VersionedSummary(
                            summary(postId, 40.0),
                            7L,
                            4L
                    )
            ));

            assertThat(summaryCache.getAll(List.of(postId)).get(postId).score()).isEqualTo(40.0);
        } finally {
            connectionFactory.destroy();
        }
    }

    private static void commitTerminalDeletion(
            RedisHotFeedProjectionGuard guard,
            RedisPostFeedCache feedCache,
            RedisPostSummaryCache summaryCache,
            RedisPostDetailCache detailCache,
            UUID postId,
            UUID payloadBoardId,
            String eventId
    ) {
        HotFeedProjectionGuard.ProjectionAttempt deletion = guard.tryBegin(postId, eventId, 5L, true);
        assertThat(deletion.accepted()).isTrue();
        assertThat(guard.isCurrent(deletion)).isTrue();
        feedCache.terminalRemove(postId, payloadBoardId);
        summaryCache.terminalEvict(postId);
        detailCache.terminalEvict(postId);
        guard.commit(deletion);
    }

    private static void writeEverySink(
            RedisPostFeedCache feedCache,
            RedisPostSummaryCache summaryCache,
            RedisPostDetailCache detailCache,
            UUID postId,
            List<UUID> boardIds
    ) {
        feedCache.upsertGlobalHot(postId, 99.0, "hot-v2");
        for (UUID boardId : boardIds) {
            feedCache.upsertBoardHot(boardId, postId, 98.0, "hot-v2");
        }
        summaryCache.putAll(List.of(summary(postId)));
        detailCache.put(postId, detail(postId));
    }

    private static void assertAbsentFromEverySink(
            StringRedisTemplate redisTemplate,
            RedisPostSummaryCache summaryCache,
            RedisPostDetailCache detailCache,
            UUID postId,
            List<UUID> boardIds
    ) {
        assertThat(redisTemplate.opsForZSet().score(globalFeedKey(), postId.toString())).isNull();
        for (UUID boardId : boardIds) {
            assertThat(redisTemplate.opsForZSet().score(boardFeedKey(boardId), postId.toString())).isNull();
        }
        assertThat(summaryCache.getAll(List.of(postId))).isEmpty();
        assertThat(detailCache.get(postId)).isNull();
    }

    private static void assertBoundedPerPostFences(
            StringRedisTemplate redisTemplate,
            UUID postId,
            List<UUID> boardIds
    ) {
        assertBoundedFeedFence(redisTemplate, globalFeedKey(), postId);
        for (UUID boardId : boardIds) {
            assertBoundedFeedFence(redisTemplate, boardFeedKey(boardId), postId);
        }
        assertBoundedStringFence(redisTemplate, summaryFenceKey(postId));
        assertBoundedStringFence(redisTemplate, detailFenceKey(postId));
    }

    private static void assertBoundedFeedFence(
            StringRedisTemplate redisTemplate,
            String feedKey,
            UUID postId
    ) {
        assertBoundedStringFence(redisTemplate, feedFenceKey(feedKey, postId));
    }

    private static void assertBoundedStringFence(StringRedisTemplate redisTemplate, String fenceKey) {
        assertThat(redisTemplate.opsForValue().get(fenceKey)).isEqualTo("1");
        assertThat(redisTemplate.getExpire(fenceKey, TimeUnit.SECONDS))
                .isBetween(
                        TERMINAL_FENCE_TTL_SECONDS - TERMINAL_FENCE_TTL_TOLERANCE_SECONDS,
                        TERMINAL_FENCE_TTL_SECONDS
                );
    }

    private static void assertBoundedVersionMarker(
            StringRedisTemplate redisTemplate,
            String markerKey,
            long expectedVersion
    ) {
        assertThat(redisTemplate.opsForValue().get(markerKey)).isEqualTo(Long.toString(expectedVersion));
        assertThat(redisTemplate.getExpire(markerKey, TimeUnit.SECONDS))
                .isBetween(
                        TERMINAL_FENCE_TTL_SECONDS - TERMINAL_FENCE_TTL_TOLERANCE_SECONDS,
                        TERMINAL_FENCE_TTL_SECONDS
                );
    }

    private static void assertAllSinkPairsShareSlots(UUID postId, List<UUID> boardIds) {
        assertPairSharesSlot(globalFeedKey(), feedFenceKey(globalFeedKey(), postId));
        for (UUID boardId : boardIds) {
            assertPairSharesSlot(boardFeedKey(boardId), feedFenceKey(boardFeedKey(boardId), postId));
        }
        assertPairSharesSlot(summaryKey(postId), summaryFenceKey(postId));
        assertPairSharesSlot(detailKey(postId), detailFenceKey(postId));
    }

    private static void assertPairSharesSlot(String cacheKey, String fenceKey) {
        assertThat(SlotHash.getSlot(fenceKey)).isEqualTo(SlotHash.getSlot(cacheKey));
    }

    private static LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private static StringRedisTemplate redisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    private static Category category(UUID id) {
        Category category = new Category();
        category.setId(id);
        return category;
    }

    private static PostSummaryResult summary(UUID postId) {
        return summary(postId, 1.0);
    }

    private static PostSummaryResult summary(UUID postId, double score) {
        return new PostSummaryResult(
                postId,
                UUID.randomUUID(),
                "title",
                "preview",
                0,
                0,
                new Date(1_000),
                0,
                score,
                UUID.randomUUID(),
                List.of(),
                null,
                null,
                null,
                ""
        );
    }

    private static PostDetailResult detail(UUID postId) {
        return new PostDetailResult(
                postId,
                UUID.randomUUID(),
                "title",
                List.of(),
                0,
                0,
                new Date(1_000),
                new Date(2_000),
                0,
                0,
                1.0,
                UUID.randomUUID(),
                List.of(),
                0L,
                false,
                false
        );
    }

    private static String globalFeedKey() {
        return "post:feed:global:hot";
    }

    private static RedisPostFeedCache newCache(
            StringRedisTemplate redisTemplate,
            FeedCursorCodec feedCursorCodec,
            CategoryContentRepository categoryContentRepository
    ) {
        return new RedisPostFeedCache(
                redisTemplate,
                feedCursorCodec,
                categoryContentRepository,
                java.time.Clock.systemUTC()
        );
    }

    private static String boardFeedKey(UUID boardId) {
        return "post:feed:board:hot:" + boardId;
    }

    private static String feedFenceKey(String feedKey, UUID postId) {
        return "post:feed:terminal-members:{" + feedKey + "}:" + postId;
    }

    private static String feedVersionKey(String feedKey, UUID postId) {
        return "post:feed:version-members:{" + feedKey + "}:" + postId;
    }

    private static String feedScoreVersionKey(String feedKey, UUID postId) {
        return "post:feed:score-version-members:{" + feedKey + "}:" + postId;
    }

    private static String summaryKey(UUID postId) {
        return "post:summary:" + postId;
    }

    private static String summaryFenceKey(UUID postId) {
        return "post:summary:terminal:{" + summaryKey(postId) + "}";
    }

    private static String summaryVersionKey(UUID postId) {
        return "post:summary:version:{" + summaryKey(postId) + "}";
    }

    private static String summaryScoreVersionKey(UUID postId) {
        return "post:summary:score-version:{" + summaryKey(postId) + "}";
    }

    private static String detailKey(UUID postId) {
        return "post:detail:" + postId;
    }

    private static String detailFenceKey(UUID postId) {
        return "post:detail:terminal:{" + detailKey(postId) + "}";
    }

    private static String detailVersionKey(UUID postId) {
        return "post:detail:version:{" + detailKey(postId) + "}";
    }

    private static String lockKey(UUID postId) {
        return "post:feed:hot:projection:lock:{" + postId + "}";
    }
}
