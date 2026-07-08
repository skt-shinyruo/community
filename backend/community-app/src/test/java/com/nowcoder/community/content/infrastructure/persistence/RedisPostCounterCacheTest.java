package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
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

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("post:counter:" + postId)).thenReturn(values);

        RedisPostCounterCache cache = new RedisPostCounterCache(redisTemplate, 86_400L);

        PostCounterSnapshot snapshot = cache.get(postId);

        assertThat(snapshot.viewCount()).isZero();
        assertThat(snapshot.likeCount()).isEqualTo(7L);
        assertThat(snapshot.commentCount()).isEqualTo(3L);
        assertThat(snapshot.bookmarkCount()).isEqualTo(2L);
        assertThat(snapshot.score()).isZero();
        verify(hashOperations).delete("post:counter:" + postId, "viewCount", "score");
    }
}
