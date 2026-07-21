package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPostFeedCacheTest {

    @Test
    void readGlobalHotIdsShouldRemoveInvalidUuidMembers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(3);

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange("post:feed:global:hot", 0L, 1L))
                .thenReturn(new LinkedHashSet<>(List.of("not-a-uuid", postId.toString())));

        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryContentRepository
        );

        List<UUID> result = cache.readGlobalHotIds("", 2);

        assertThat(result).containsExactly(postId);
        verify(zSetOperations).remove("post:feed:global:hot", "not-a-uuid");
    }

    @Test
    void readRankVersionShouldDeleteBlankPayloadAndReturnDefault() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:feed:global:hot:rank-version")).thenReturn(" ");

        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryContentRepository
        );

        assertThat(cache.readRankVersion()).isEqualTo("hot-v2");
        verify(redisTemplate).delete("post:feed:global:hot:rank-version");
    }

    @Test
    void removeShouldDeletePostFromAllKnownBoardFeedsWhenBoardIdMissing() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(9);
        Category first = category(uuid(1));
        Category second = category(uuid(2));

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(categoryContentRepository.listCategories()).thenReturn(List.of(first, second));

        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryContentRepository
        );

        cache.remove(postId, null);

        verify(zSetOperations).remove("post:feed:global:hot", postId.toString());
        verify(zSetOperations).remove("post:feed:board:hot:" + first.getId(), postId.toString());
        verify(zSetOperations).remove("post:feed:board:hot:" + second.getId(), postId.toString());
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class), any(Object[].class));
    }

    @Test
    void upsertShouldAtomicallyCheckScopeTerminalMembersBeforeWriting() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(8);
        UUID boardId = uuid(18);
        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryContentRepository
        );

        cache.upsertGlobalHot(postId, 42.5, "hot-v2");
        cache.upsertBoardHot(boardId, postId, 41.5, "hot-v2");

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:global:hot",
                        "post:feed:terminal-members:{post:feed:global:hot}"
                )),
                eq(postId.toString()),
                eq("42.5")
        );
        String boardKey = "post:feed:board:hot:" + boardId;
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(boardKey, "post:feed:terminal-members:{" + boardKey + "}")),
                eq(postId.toString()),
                eq("41.5")
        );
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
        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
                categoryContentRepository
        );

        cache.terminalRemove(postId, payloadBoardId);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        "post:feed:global:hot",
                        "post:feed:terminal-members:{post:feed:global:hot}"
                )),
                eq(postId.toString())
        );
        verifyTerminalBoardRemoval(redisTemplate, postId, payloadBoardId);
        verifyTerminalBoardRemoval(redisTemplate, postId, otherBoardId);
    }

    @Test
    void terminalRemoveShouldFailWhenLuaDoesNotConfirmFencePersistence() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        CategoryContentRepository categoryContentRepository = mock(CategoryContentRepository.class);
        UUID postId = uuid(10);
        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
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
        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
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

        RedisPostFeedCache cache = new RedisPostFeedCache(
                redisTemplate,
                new FeedCursorCodec(),
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

    private static void verifyTerminalBoardRemoval(
            StringRedisTemplate redisTemplate,
            UUID postId,
            UUID boardId
    ) {
        String boardKey = "post:feed:board:hot:" + boardId;
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(boardKey, "post:feed:terminal-members:{" + boardKey + "}")),
                eq(postId.toString())
        );
    }
}
