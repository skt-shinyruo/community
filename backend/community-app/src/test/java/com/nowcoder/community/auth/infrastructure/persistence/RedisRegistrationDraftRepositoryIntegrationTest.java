package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.model.PreparedRegistrationDraft;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRegistrationDraftRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void activatedMarkerShouldReplaceSensitiveDraftAndRetainTheLongerStateHorizon() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationDraftRepository repository = new RedisRegistrationDraftRepository(
                redis, new JacksonJsonCodec(JsonMappers.standard()));
        UUID userId = uuid(1);
        PreparedRegistrationDraft draft = draft(userId);
        try {
            assertThat(repository.store("token", draft, Duration.ofMinutes(5))).isTrue();

            assertThat(repository.markActivated("token", userId, Duration.ofMinutes(1))).isTrue();

            assertThat(repository.find("token")).isEmpty();
            assertThat(repository.findActivatedUserId("token")).contains(userId);
            assertThat(redis.opsForValue().get("auth:regdraft:token"))
                    .isEqualTo("ACTIVATED:" + userId);
            assertThat(redis.getExpire("auth:regdraft:token"))
                    .isGreaterThanOrEqualTo(240L);
            assertThat(repository.markActivated("token", userId, Duration.ofMinutes(1))).isTrue();
            assertThat(repository.markActivated("token", uuid(2), Duration.ofMinutes(1))).isFalse();

            repository.delete("token");

            assertThat(repository.findActivatedUserId("token")).contains(userId);
        } finally {
            connectionFactory.destroy();
        }
    }

    private static LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT));
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate redisTemplate(LettuceConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    private static PreparedRegistrationDraft draft(UUID userId) {
        Instant now = Instant.now();
        return new PreparedRegistrationDraft(
                userId,
                "alice",
                "alice@example.com",
                "encoded-password",
                "h",
                now,
                now.plus(Duration.ofMinutes(5))
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
