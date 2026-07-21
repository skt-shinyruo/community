package com.nowcoder.community.content.infrastructure.persistence;

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
        RedisPostFeedCache feedCache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
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
            assertPermanentFences(redisTemplate, lateWriterPostId, boardIds);
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
            assertPermanentFences(redisTemplate, earlyWriterPostId, boardIds);
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
        RedisPostFeedCache feedCache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryRepository
        );
        JsonCodec jsonCodec = new JacksonJsonCodec(JsonMappers.standard());
        RedisPostSummaryCache summaryCache = new RedisPostSummaryCache(redisTemplate, jsonCodec);
        RedisPostDetailCache detailCache = new RedisPostDetailCache(redisTemplate, jsonCodec);
        try {
            feedCache.remove(postId, null);
            summaryCache.evictAll(List.of(postId));
            detailCache.evict(postId);

            assertThat(redisTemplate.opsForSet().isMember(feedFenceKey(globalFeedKey()), postId.toString())).isFalse();
            assertThat(redisTemplate.opsForSet().isMember(
                    feedFenceKey(boardFeedKey(payloadBoardId)),
                    postId.toString()
            )).isFalse();
            assertThat(redisTemplate.opsForSet().isMember(
                    feedFenceKey(boardFeedKey(categoryId)),
                    postId.toString()
            )).isFalse();
            assertThat(redisTemplate.hasKey(summaryFenceKey(postId))).isFalse();
            assertThat(redisTemplate.hasKey(detailFenceKey(postId))).isFalse();
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

    private static void assertPermanentFences(
            StringRedisTemplate redisTemplate,
            UUID postId,
            List<UUID> boardIds
    ) {
        assertPermanentFeedFence(redisTemplate, globalFeedKey(), postId);
        for (UUID boardId : boardIds) {
            assertPermanentFeedFence(redisTemplate, boardFeedKey(boardId), postId);
        }
        assertThat(redisTemplate.hasKey(summaryFenceKey(postId))).isTrue();
        assertThat(redisTemplate.getExpire(summaryFenceKey(postId), TimeUnit.SECONDS)).isEqualTo(-1L);
        assertThat(redisTemplate.hasKey(detailFenceKey(postId))).isTrue();
        assertThat(redisTemplate.getExpire(detailFenceKey(postId), TimeUnit.SECONDS)).isEqualTo(-1L);
    }

    private static void assertPermanentFeedFence(
            StringRedisTemplate redisTemplate,
            String feedKey,
            UUID postId
    ) {
        String fenceKey = feedFenceKey(feedKey);
        assertThat(redisTemplate.opsForSet().isMember(fenceKey, postId.toString())).isTrue();
        assertThat(redisTemplate.getExpire(fenceKey, TimeUnit.SECONDS)).isEqualTo(-1L);
    }

    private static void assertAllSinkPairsShareSlots(UUID postId, List<UUID> boardIds) {
        assertPairSharesSlot(globalFeedKey(), feedFenceKey(globalFeedKey()));
        for (UUID boardId : boardIds) {
            assertPairSharesSlot(boardFeedKey(boardId), feedFenceKey(boardFeedKey(boardId)));
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
        return new PostSummaryResult(
                postId,
                UUID.randomUUID(),
                "title",
                "preview",
                0,
                0,
                new Date(1_000),
                0,
                1.0,
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

    private static String boardFeedKey(UUID boardId) {
        return "post:feed:board:hot:" + boardId;
    }

    private static String feedFenceKey(String feedKey) {
        return "post:feed:terminal-members:{" + feedKey + "}";
    }

    private static String summaryKey(UUID postId) {
        return "post:summary:" + postId;
    }

    private static String summaryFenceKey(UUID postId) {
        return "post:summary:terminal:{" + summaryKey(postId) + "}";
    }

    private static String detailKey(UUID postId) {
        return "post:detail:" + postId;
    }

    private static String detailFenceKey(UUID postId) {
        return "post:detail:terminal:{" + detailKey(postId) + "}";
    }

    private static String lockKey(UUID postId) {
        return "post:feed:hot:projection:lock:{" + postId + "}";
    }
}
