package com.nowcoder.community.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenStoreTest {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setupRedisClient() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @BeforeEach
    void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void redisStoreShouldWorkAcrossInstances() {
        RedisRefreshTokenStore store1 = new RedisRefreshTokenStore(redisTemplate, objectMapper);
        RedisRefreshTokenStore store2 = new RedisRefreshTokenStore(redisTemplate, objectMapper);

        String refreshToken = "rt-" + System.nanoTime();
        String familyId = "fam-" + System.nanoTime();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        store1.store(refreshToken, 1, familyId, expiresAt);

        RefreshTokenStore.StoredRefreshToken found = store2.find(refreshToken);
        assertThat(found).isNotNull();
        assertThat(found.userId()).isEqualTo(1);
        assertThat(found.familyId()).isEqualTo(familyId);
    }

    @Test
    void revokeFamilyShouldInvalidateAllTokensInFamily() {
        RedisRefreshTokenStore store = new RedisRefreshTokenStore(redisTemplate, objectMapper);

        String familyId = "fam-" + System.nanoTime();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        String t1 = "rt-1-" + System.nanoTime();
        String t2 = "rt-2-" + System.nanoTime();
        store.store(t1, 1, familyId, expiresAt);
        store.store(t2, 1, familyId, expiresAt);

        store.revokeFamily(familyId);

        assertThat(store.find(t1)).isNull();
        assertThat(store.find(t2)).isNull();
    }
}

