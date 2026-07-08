package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.FeedCursorCodec;
import com.nowcoder.community.content.domain.model.Category;
import com.nowcoder.community.content.domain.repository.CategoryContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
}
