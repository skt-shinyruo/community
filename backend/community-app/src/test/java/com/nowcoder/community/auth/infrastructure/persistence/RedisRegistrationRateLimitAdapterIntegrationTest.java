package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.port.RegistrationRateLimitPort;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisRegistrationRateLimitAdapterIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void exhaustedDimensionShouldNotPartiallyConsumeOtherBuckets() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redis);
        try {
            assertThat(adapter.tryConsume(
                    RegistrationRateLimitPort.Flow.RESEND,
                    Duration.ofMinutes(5),
                    quotas("ip-a", "email-shared", "registration-a", 2, 1, 2)
            )).isTrue();

            assertThat(adapter.tryConsume(
                    RegistrationRateLimitPort.Flow.RESEND,
                    Duration.ofMinutes(5),
                    quotas("ip-b", "email-shared", "registration-b", 1, 1, 1)
            )).isFalse();

            // The denied call must not have consumed ip-b or registration-b.
            assertThat(adapter.tryConsume(
                    RegistrationRateLimitPort.Flow.RESEND,
                    Duration.ofMinutes(5),
                    quotas("ip-b", "email-other", "registration-b", 1, 1, 1)
            )).isTrue();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentConsumersShouldNeverExceedTheSharedBudget() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redis);
        int attempts = 24;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            CountDownLatch ready = new CountDownLatch(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>(attempts);
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return adapter.tryConsume(
                            RegistrationRateLimitPort.Flow.REQUEST,
                            Duration.ofMinutes(5),
                            quotas("concurrent-ip", "concurrent-email", "concurrent-registration", 5, 5, 5)
                    );
                }));
            }
            ready.await();
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
            connectionFactory.destroy();
        }
    }

    @Test
    void counterWithoutTtlShouldFailClosed() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redis);
        List<RegistrationRateLimitPort.Quota> quotas = quotas(
                "corrupt-ip", "corrupt-email", "corrupt-registration", 5, 5, 5);
        String corruptKey = RedisRegistrationRateLimitAdapter.quotaKey(
                RegistrationRateLimitPort.Flow.RESEND,
                quotas.get(1)
        );
        try {
            redis.opsForValue().set(corruptKey, "1");

            assertThatThrownBy(() -> adapter.tryConsume(
                    RegistrationRateLimitPort.Flow.RESEND,
                    Duration.ofMinutes(5),
                    quotas
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("malformed");
            String untouchedIpKey = RedisRegistrationRateLimitAdapter.quotaKey(
                    RegistrationRateLimitPort.Flow.RESEND,
                    quotas.get(0)
            );
            assertThat(redis.hasKey(untouchedIpKey)).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void nonCanonicalIntegerShouldFailBeforeAnyBucketIsMutated() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationRateLimitAdapter adapter = new RedisRegistrationRateLimitAdapter(redis);
        List<RegistrationRateLimitPort.Quota> quotas = quotas(
                "numeric-ip", "numeric-email", "numeric-registration", 5, 5, 5);
        String corruptKey = RedisRegistrationRateLimitAdapter.quotaKey(
                RegistrationRateLimitPort.Flow.RESEND,
                quotas.get(1)
        );
        try {
            redis.opsForValue().set(corruptKey, "1e2", Duration.ofMinutes(5));

            assertThatThrownBy(() -> adapter.tryConsume(
                    RegistrationRateLimitPort.Flow.RESEND,
                    Duration.ofMinutes(5),
                    quotas
            )).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("malformed");
            assertThat(redis.hasKey(RedisRegistrationRateLimitAdapter.quotaKey(
                    RegistrationRateLimitPort.Flow.RESEND,
                    quotas.get(0)
            ))).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private static List<RegistrationRateLimitPort.Quota> quotas(
            String ip,
            String email,
            String registration,
            int ipMaximum,
            int emailMaximum,
            int registrationMaximum
    ) {
        return List.of(
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.IP, ip, ipMaximum),
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.EMAIL, email, emailMaximum),
                new RegistrationRateLimitPort.Quota(
                        RegistrationRateLimitPort.Dimension.REGISTRATION, registration, registrationMaximum)
        );
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
