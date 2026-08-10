package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisPasswordResetTokenRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000042");

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void leasesShouldFenceCompetingConfirmationsAndGenerationMarkerShouldSpareNewTokens() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());
        UUID firstLease = UUID.randomUUID();
        UUID wrongLease = UUID.randomUUID();
        try {
            repository.store("sibling-a", USER_ID, 17L, Duration.ofMinutes(5));
            repository.store("sibling-b", USER_ID, 17L, Duration.ofMinutes(5));

            PasswordResetTokenRepository.PendingPasswordResetToken first = repository.beginConfirmation(
                    "sibling-a",
                    Instant.now().plusSeconds(30),
                    firstLease
            );
            assertThat(first.confirmationLeaseId()).isEqualTo(firstLease);
            assertThat(repository.beginConfirmation(
                    "sibling-a",
                    Instant.now().plusSeconds(30),
                    wrongLease
            )).isNull();
            assertThat(repository.rollbackConfirmation("sibling-a", USER_ID, 17L, wrongLease)).isFalse();
            assertThat(repository.rollbackConfirmation("sibling-a", USER_ID, 17L, firstLease)).isTrue();

            UUID finishingLease = UUID.randomUUID();
            assertThat(repository.beginConfirmation(
                    "sibling-a",
                    Instant.now().plusSeconds(30),
                    finishingLease
            )).isNotNull();
            repository.revokeGeneration(USER_ID, 17L, Duration.ofMinutes(5));
            assertThat(repository.finishConfirmation("sibling-a", USER_ID, 17L, finishingLease)).isTrue();
            assertThat(repository.beginConfirmation(
                    "sibling-b",
                    Instant.now().plusSeconds(30),
                    UUID.randomUUID()
            )).isNull();

            repository.store("new-generation", USER_ID, 18L, Duration.ofMinutes(5));
            assertThat(repository.beginConfirmation(
                    "new-generation",
                    Instant.now().plusSeconds(30),
                    UUID.randomUUID()
            )).isNotNull();

            Set<String> keys = redis.keys("auth:pwdreset:*");
            assertThat(keys).isNotNull().noneMatch(key -> key.contains("sibling-a")
                    || key.contains("sibling-b")
                    || key.contains("new-generation"));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void expiredLeaseShouldBeRecoverableWithoutAllowingTheOldOwnerToRollback() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisPasswordResetTokenRepository repository = new RedisPasswordResetTokenRepository(redis, Clock.systemUTC());
        UUID expiredLease = UUID.randomUUID();
        UUID currentLease = UUID.randomUUID();
        try {
            repository.store("recoverable", USER_ID, 19L, Duration.ofMinutes(5));
            assertThat(repository.beginConfirmation(
                    "recoverable",
                    Instant.now().minusMillis(1),
                    expiredLease
            )).isNotNull();

            PasswordResetTokenRepository.PendingPasswordResetToken recovered = repository.beginConfirmation(
                    "recoverable",
                    Instant.now().plusSeconds(30),
                    currentLease
            );

            assertThat(recovered.confirmationLeaseId()).isEqualTo(currentLease);
            assertThat(repository.rollbackConfirmation("recoverable", USER_ID, 19L, expiredLease)).isFalse();
            assertThat(repository.rollbackConfirmation("recoverable", USER_ID, 19L, currentLease)).isTrue();
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
}
