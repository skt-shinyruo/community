package com.nowcoder.community.content.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.application.PostFeedCache;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisPostFeedCacheTest {

    @Test
    void readRankVersionShouldDeleteBlankPayloadAndReturnDefault() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:feed:global:hot:rank-version")).thenReturn(" ");

        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        assertThat(cache.readRankVersion()).isEqualTo("hot-v2");
        verify(redisTemplate).delete("post:feed:global:hot:rank-version");
    }

    @Test
    void removeShouldAtomicallyDeleteLegacyAndProjectionMembersFromEveryScope() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(9);
        Category first = category(uuid(1));
        Category second = category(uuid(2));

        when(categoryContentRepository.listCategories()).thenReturn(List.of(first, second));

        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        cache.remove(postId, null);

        verifyRemoval(redisTemplate, postId, "post:feed:global:hot");
        verifyRemoval(redisTemplate, postId, "post:feed:board:hot:" + first.getId());
        verifyRemoval(redisTemplate, postId, "post:feed:board:hot:" + second.getId());
    }

    @Test
    void projectionUpsertShouldAtomicallyCheckScopeTerminalMembersBeforeWriting() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(8);
        UUID boardId = uuid(18);
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        PostFeedCache.HotProjectionEntry globalEntry = projection(postId, 0, 42.5, 1_000L);
        PostFeedCache.HotProjectionEntry boardEntry = projection(postId, 0, 41.5, 1_000L);
        cache.upsertGlobalHot(globalEntry, "hot-v2", 0L, 0L);
        cache.upsertBoardHot(boardId, boardEntry, "hot-v2", 0L, 0L);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:global:hot",
                        "post:feed:terminal-members:{post:feed:global:hot}:" + postId,
                        "post:feed:version-members:{post:feed:global:hot}:" + postId,
                        "post:feed:score-version-members:{post:feed:global:hot}:" + postId,
                        "post:feed:projection:{post:feed:global:hot}",
                        "post:feed:projection-member:{post:feed:global:hot}:" + postId,
                        "post:feed:projection-epoch:{post:feed:global:hot}"
                )),
                eq(postId.toString()),
                eq("42.5"),
                eq("0"),
                eq("0"),
                eq("604800"),
                eq(RedisPostFeedCache.projectionMember(globalEntry))
        );
        String boardKey = "post:feed:board:hot:" + boardId;
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        boardKey,
                        "post:feed:terminal-members:{" + boardKey + "}:" + postId,
                        "post:feed:version-members:{" + boardKey + "}:" + postId,
                        "post:feed:score-version-members:{" + boardKey + "}:" + postId,
                        "post:feed:projection:{" + boardKey + "}",
                        "post:feed:projection-member:{" + boardKey + "}:" + postId,
                        "post:feed:projection-epoch:{" + boardKey + "}"
                )),
                eq(postId.toString()),
                eq("41.5"),
                eq("0"),
                eq("0"),
                eq("604800"),
                eq(RedisPostFeedCache.projectionMember(boardEntry))
        );
    }

    @Test
    void projectionMemberShouldMatchDatabaseTotalOrderAndRoundTrip() {
        List<PostFeedCache.HotProjectionEntry> entries = List.of(
                projection(uuid(1), 0, 50.0, 1_000L),
                projection(uuid(2), 0, 50.0, 2_000L),
                projection(uuid(3), 0, 75.0, 1_000L),
                projection(uuid(4), 1, -10.0, 500L),
                projection(uuid(5), 0, 50.0, 2_000L)
        );
        Comparator<PostFeedCache.HotProjectionEntry> databaseOrder =
                Comparator.comparingInt(PostFeedCache.HotProjectionEntry::type).reversed()
                        .thenComparing(Comparator.comparingDouble(PostFeedCache.HotProjectionEntry::score).reversed())
                        .thenComparing(PostFeedCache.HotProjectionEntry::createTime, Comparator.reverseOrder())
                        .thenComparing(PostFeedCache.HotProjectionEntry::postId, Comparator.reverseOrder());

        List<PostFeedCache.HotProjectionEntry> expected = entries.stream().sorted(databaseOrder).toList();
        List<PostFeedCache.HotProjectionEntry> encodedOrder = entries.stream()
                .sorted(Comparator.comparing(RedisPostFeedCache::projectionMember).reversed())
                .toList();

        assertThat(encodedOrder).isEqualTo(expected);
        assertThat(entries).allSatisfy(entry -> assertThat(
                RedisPostFeedCache.parseProjectionMember(RedisPostFeedCache.projectionMember(entry))
        ).isEqualTo(entry));
    }

    @Test
    void readProjectionShouldUseStableEpochAndOneRowLookahead() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        PostFeedCache.HotProjectionEntry first = projection(uuid(6), 1, 1.0, 3_000L);
        PostFeedCache.HotProjectionEntry second = projection(uuid(7), 0, 9.0, 2_000L);
        PostFeedCache.HotProjectionEntry lookahead = projection(uuid(8), 0, 8.0, 1_000L);
        String projectionKey = "post:feed:projection:{post:feed:global:hot}";
        String epochKey = "post:feed:projection-epoch:{post:feed:global:hot}";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get(epochKey)).thenReturn("7", "7");
        when(zSetOperations.reverseRangeByLex(
                eq(projectionKey), any(Range.class), any(Limit.class)
        )).thenReturn(new LinkedHashSet<>(List.of(
                RedisPostFeedCache.projectionMember(first),
                RedisPostFeedCache.projectionMember(second),
                RedisPostFeedCache.projectionMember(lookahead)
        )));
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        PostFeedCache.HotProjectionPage page = cache.readGlobalHotProjection("", 2);

        assertThat(page.entries()).containsExactly(first, second);
        assertThat(page.epoch()).isEqualTo(7L);
        assertThat(page.hasNext()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Range<String>> rangeCaptor = ArgumentCaptor.forClass(Range.class);
        ArgumentCaptor<Limit> limitCaptor = ArgumentCaptor.forClass(Limit.class);
        verify(zSetOperations).reverseRangeByLex(eq(projectionKey), rangeCaptor.capture(), limitCaptor.capture());
        assertThat(rangeCaptor.getValue()).isEqualTo(Range.unbounded());
        assertThat(limitCaptor.getValue().getCount()).isEqualTo(3L);
        verify(zSetOperations, never()).reverseRange(anyString(), any(Long.class), any(Long.class));
    }

    @Test
    void projectionEpochMismatchShouldFailClosedBeforeReadingMembers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        FeedCursorCodec cursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        FeedCursorCodec.HotBoundary boundary = new FeedCursorCodec.HotBoundary(
                0, 10.0, new Date(1_000L), uuid(9));
        String cursor = cursorCodec.encodeHotPage(1, 2, boundary, 7L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(valueOperations.get("post:feed:projection-epoch:{post:feed:global:hot}"))
                .thenReturn("8");
        RedisPostFeedCache cache = newCache(redisTemplate, cursorCodec, categoryContentRepository);

        PostFeedCache.HotProjectionPage page = cache.readGlobalHotProjection(cursor, 2);

        assertThat(page.entries()).isEmpty();
        assertThat(page.epoch()).isZero();
        verifyNoInteractions(zSetOperations);
    }

    @Test
    void legacyOffsetCursorShouldFailClosedBeforeReadingProjectionMembers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        FeedCursorCodec cursorCodec = new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper()));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        RedisPostFeedCache cache = newCache(redisTemplate, cursorCodec, mock(CategoryContentRepository.class));

        PostFeedCache.HotProjectionPage page = cache.readGlobalHotProjection(cursorCodec.encodePage(1, 20), 20);

        assertThat(page.entries()).isEmpty();
        assertThat(page.epoch()).isZero();
        verifyNoInteractions(zSetOperations);
    }

    @Test
    void projectionUpsertShouldKeepMemberIndexUntilExplicitRemoval() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        cache.upsertGlobalHot(projection(uuid(10), 0, 42.0, 1_000L), "hot-v2", 7L, 3L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), any(List.class), any(Object[].class));
        assertThat(scriptCaptor.getValue().getScriptAsString())
                .contains("redis.call('SET', KEYS[6], ARGV[6])")
                .contains("if removed > 0 or added > 0 then")
                .doesNotContain("redis.call('SET', KEYS[6], ARGV[6], 'EX'");
    }

    @Test
    void terminalRemoveShouldFenceGlobalPayloadBoardAndEveryCurrentCategoryOnce() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(9);
        UUID payloadBoardId = uuid(19);
        UUID otherBoardId = uuid(29);
        when(categoryContentRepository.listCategories()).thenReturn(List.of(
                category(payloadBoardId),
                category(otherBoardId),
                category(payloadBoardId)
        ));
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(1L);
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        cache.terminalRemove(postId, payloadBoardId);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:global:hot",
                        "post:feed:terminal-members:{post:feed:global:hot}:" + postId,
                        "post:feed:version-members:{post:feed:global:hot}:" + postId,
                        "post:feed:projection:{post:feed:global:hot}",
                        "post:feed:projection-member:{post:feed:global:hot}:" + postId,
                        "post:feed:projection-epoch:{post:feed:global:hot}"
                )),
                eq(postId.toString()),
                eq("604800"),
                eq("0")
        );
        verifyTerminalBoardRemoval(redisTemplate, postId, payloadBoardId);
        verifyTerminalBoardRemoval(redisTemplate, postId, otherBoardId);
    }

    @Test
    void terminalRemoveShouldFailWhenLuaDoesNotConfirmFencePersistence() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(10);
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        assertThatThrownBy(() -> cache.terminalRemove(postId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("post feed terminal fence was not persisted")
                .hasMessageContaining(postId.toString());
    }

    @Test
    void terminalRemoveShouldPropagateCategoryListingFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(11);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(Object[].class))).thenReturn(1L);
        when(categoryContentRepository.listCategories()).thenThrow(new IllegalStateException("category store unavailable"));
        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        assertThatThrownBy(() -> cache.terminalRemove(postId, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("category store unavailable");
    }

    @Test
    void rankVersionShouldRoundTripThroughRedisValueStorage() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:feed:global:hot:rank-version")).thenReturn("hot-v2");

        RedisPostFeedCache cache = newCache(
                redisTemplate,
                new FeedCursorCodec(new JacksonJsonCodec(new ObjectMapper())),
                categoryContentRepository
        );

        cache.writeRankVersion("hot-v2");

        verify(valueOperations).set("post:feed:global:hot:rank-version", "hot-v2");
        org.assertj.core.api.Assertions.assertThat(cache.readRankVersion()).isEqualTo("hot-v2");
    }

    private static Category category(UUID id) {
        Category category = new Category();
        category.setId(id);
        return category;
    }

    private static PostFeedCache.HotProjectionEntry projection(
            UUID postId,
            int type,
            double score,
            long createTime
    ) {
        return new PostFeedCache.HotProjectionEntry(postId, type, score, new Date(createTime));
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

    private static void verifyTerminalBoardRemoval(
            StringRedisTemplate redisTemplate,
            UUID postId,
            UUID boardId
    ) {
        String boardKey = "post:feed:board:hot:" + boardId;
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        boardKey,
                        "post:feed:terminal-members:{" + boardKey + "}:" + postId,
                        "post:feed:version-members:{" + boardKey + "}:" + postId,
                        "post:feed:projection:{" + boardKey + "}",
                        "post:feed:projection-member:{" + boardKey + "}:" + postId,
                        "post:feed:projection-epoch:{" + boardKey + "}"
                )),
                eq(postId.toString()),
                eq("604800"),
                eq("0")
        );
    }

    private static void verifyRemoval(StringRedisTemplate redisTemplate, UUID postId, String feedKey) {
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        feedKey,
                        "post:feed:version-members:{" + feedKey + "}:" + postId,
                        "post:feed:projection:{" + feedKey + "}",
                        "post:feed:projection-member:{" + feedKey + "}:" + postId,
                        "post:feed:projection-epoch:{" + feedKey + "}"
                )),
                eq(postId.toString()),
                eq("0"),
                eq("604800")
        );
    }
}
