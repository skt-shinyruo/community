package com.nowcoder.community.auth.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisLoginRateLimitRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final String FAILURE_KEY = "auth:login:fail:user:alice";
    private static final String LEASE_KEY =
            "auth:login:inflight:{auth:login:fail:user:alice}:auth:login:fail:user:alice";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void committedFailuresAndInFlightChecksShouldShareOneAtomicBudget() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redis);
        List<UUID> initialLeases = new ArrayList<>();
        try {
            for (int i = 0; i < 5; i++) {
                UUID token = UUID.randomUUID();
                assertThat(repository.tryAcquire(FAILURE_KEY, LEASE_KEY, token, 5, 30_000)).isTrue();
                initialLeases.add(token);
            }

            // One running check commits its failure before releasing its lease.
            assertThat(repository.increment(FAILURE_KEY, 60)).isEqualTo(1);
            assertThat(repository.countBudget(FAILURE_KEY, LEASE_KEY)).isEqualTo(6);
            repository.release(LEASE_KEY, initialLeases.remove(0));
            assertThat(repository.countBudget(FAILURE_KEY, LEASE_KEY)).isEqualTo(5);

            // A request that passed an earlier HTTP pre-check still cannot enter:
            // one committed failure plus four live leases already consumes the budget.
            assertThat(repository.tryAcquire(
                    FAILURE_KEY, LEASE_KEY, UUID.randomUUID(), 5, 30_000)).isFalse();

            initialLeases.forEach(token -> repository.release(LEASE_KEY, token));
            List<UUID> laterLeases = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                UUID token = UUID.randomUUID();
                assertThat(repository.tryAcquire(FAILURE_KEY, LEASE_KEY, token, 5, 30_000)).isTrue();
                laterLeases.add(token);
            }
            assertThat(repository.tryAcquire(
                    FAILURE_KEY, LEASE_KEY, UUID.randomUUID(), 5, 30_000)).isFalse();
            laterLeases.forEach(token -> repository.release(LEASE_KEY, token));
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void renewalShouldExtendOnlyTheStillLiveOwnerToken() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redis);
        UUID token = UUID.randomUUID();
        String failureKey = "auth:login:fail:user:renewal";
        String leaseKey = "auth:login:inflight:{" + failureKey + "}:" + failureKey;
        try {
            assertThat(repository.tryAcquire(failureKey, leaseKey, token, 1, 30_000)).isTrue();
            Double before = redis.opsForZSet().score(leaseKey, token.toString());

            assertThat(repository.renew(leaseKey, token, 120_000)).isTrue();
            Double after = redis.opsForZSet().score(leaseKey, token.toString());

            assertThat(before).isNotNull();
            assertThat(after).isNotNull().isGreaterThan(before + 80_000D);

            redis.opsForZSet().add(leaseKey, token.toString(), 0D);
            assertThat(repository.renew(leaseKey, token, 120_000)).isFalse();
            assertThat(redis.opsForZSet().score(leaseKey, token.toString())).isNull();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void shorterAcquisitionShouldNotExpireAnotherLongerLiveLease() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redis);
        UUID longLease = UUID.randomUUID();
        UUID shortLease = UUID.randomUUID();
        String failureKey = "auth:login:fail:user:mixed-acquire";
        String leaseKey = "auth:login:inflight:{" + failureKey + "}:" + failureKey;
        try {
            assertThat(repository.tryAcquire(failureKey, leaseKey, longLease, 2, 120_000)).isTrue();
            assertThat(repository.tryAcquire(failureKey, leaseKey, shortLease, 2, 30_000)).isTrue();

            assertThat(redis.getExpire(leaseKey, TimeUnit.MILLISECONDS))
                    .isGreaterThan(90_000L);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void shorterRenewalShouldNotExpireAnotherLongerLiveLease() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redis);
        UUID longLease = UUID.randomUUID();
        UUID shortLease = UUID.randomUUID();
        String failureKey = "auth:login:fail:user:mixed-renew";
        String leaseKey = "auth:login:inflight:{" + failureKey + "}:" + failureKey;
        try {
            assertThat(repository.tryAcquire(failureKey, leaseKey, longLease, 2, 120_000)).isTrue();
            assertThat(repository.tryAcquire(failureKey, leaseKey, shortLease, 2, 120_000)).isTrue();
            assertThat(repository.renew(leaseKey, shortLease, 30_000)).isTrue();

            assertThat(redis.getExpire(leaseKey, TimeUnit.MILLISECONDS))
                    .isGreaterThan(90_000L);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void malformedCommittedFailureBudgetsShouldFailClosedWithoutCreatingALease() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redis);
        try {
            for (String malformed : List.of("junk", "1e2", "-2", "1.5")) {
                redis.delete(List.of(FAILURE_KEY, LEASE_KEY));
                redis.opsForValue().set(FAILURE_KEY, malformed, 60, TimeUnit.SECONDS);

                assertMalformedBudgetFailsClosed(repository);
                assertThat(redis.hasKey(LEASE_KEY)).isFalse();
            }

            redis.delete(List.of(FAILURE_KEY, LEASE_KEY));
            redis.opsForValue().set(FAILURE_KEY, "2");

            assertMalformedBudgetFailsClosed(repository);
            assertThat(redis.hasKey(LEASE_KEY)).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private static void assertMalformedBudgetFailsClosed(RedisLoginRateLimitRepository repository) {
        assertThatThrownBy(() -> repository.tryAcquire(
                FAILURE_KEY, LEASE_KEY, UUID.randomUUID(), 5, 30_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failure budget");
        assertThatThrownBy(() -> repository.countBudget(FAILURE_KEY, LEASE_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failure budget");
        assertThatThrownBy(() -> repository.increment(FAILURE_KEY, 60))
                .isInstanceOf(IllegalStateException.class);
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
