package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.domain.model.PostCounterSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisPostCounterCacheIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void corruptViewBaselineShouldPreservePendingDeltaAcrossReinitialization() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisPostCounterCache cache = newCache(redis, 86_400L);
        UUID postId = UUID.randomUUID();
        String key = counterKey(postId);
        try {
            redis.opsForHash().putAll(key, Map.of(
                    "initialized", "1",
                    "baseViewCount", "corrupt",
                    "baseLikeCount", "2",
                    "baseCommentCount", "3",
                    "baseBookmarkCount", "4",
                    "baseScore", "8.5",
                    "baseRevision", "12",
                    "deltaViewCount", "5"
            ));

            assertThat(cache.get(postId).viewCount()).isZero();
            assertThat(cache.isInitialized(postId)).isFalse();
            assertThat(redis.opsForHash().get(key, "recoveryViewDelta")).isEqualTo("5");

            PostCounterSnapshot recoveredBaseline = new PostCounterSnapshot(
                    postId,
                    100L,
                    2L,
                    3L,
                    4L,
                    8.5,
                    12L
            );
            cache.initializeIfAbsent(recoveredBaseline);

            assertThat(cache.get(postId).viewCount()).isEqualTo(105L);
            assertThat(cache.recordView(postId, "viewer:new", Instant.EPOCH, recoveredBaseline)).isTrue();
            assertThat(cache.get(postId).viewCount()).isEqualTo(106L);
            assertThat(redis.opsForHash().hasKey(key, "recoveryViewDelta")).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void corruptInitializationMarkerShouldPreserveBaseAndPendingDelta() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisPostCounterCache cache = newCache(redis, 86_400L);
        UUID postId = UUID.randomUUID();
        String key = counterKey(postId);
        try {
            redis.opsForHash().putAll(key, Map.of(
                    "initialized", "corrupt",
                    "baseViewCount", "10",
                    "baseLikeCount", "2",
                    "baseCommentCount", "3",
                    "baseBookmarkCount", "4",
                    "baseScore", "8.5",
                    "baseRevision", "12",
                    "deltaViewCount", "3"
            ));

            assertThat(cache.get(postId).viewCount()).isEqualTo(10L);
            assertThat(cache.isInitialized(postId)).isFalse();

            cache.initializeIfAbsent(new PostCounterSnapshot(postId, 10L, 2L, 3L, 4L, 8.5, 12L));

            assertThat(cache.isInitialized(postId)).isTrue();
            assertThat(cache.get(postId).viewCount()).isEqualTo(13L);
            assertThat(redis.opsForHash().hasKey(key, "recoveryViewDelta")).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private static LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate redisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
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

    private static String counterKey(UUID postId) {
        int shard = Math.floorMod(postId.hashCode(), 32);
        String value = Integer.toHexString(shard);
        String tag = "{post-counter-" + (value.length() == 1 ? "0" + value : value) + "}";
        return "post:counter:v2:" + tag + ":" + postId;
    }
}
