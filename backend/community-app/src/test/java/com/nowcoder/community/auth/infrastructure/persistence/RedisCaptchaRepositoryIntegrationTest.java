package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ClusterSlotHashUtil;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisCaptchaRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void concurrentCorrectVerificationsShouldConsumeExactlyOnce() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisCaptchaRepository repository = new RedisCaptchaRepository(redis);
        String captchaId = "10000000000000000000000000000001";
        int attempts = 16;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            repository.save(captchaId, "AbC1", Duration.ofSeconds(30));
            CountDownLatch ready = new CountDownLatch(attempts);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<CaptchaRepository.VerifyResult>> results = java.util.stream.IntStream.range(0, attempts)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return repository.verifyAndConsume(captchaId, "aBc1", 3, Duration.ofSeconds(30));
                    }))
                    .toList();

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<CaptchaRepository.VerifyResult> outcomes = results.stream().map(this::get).toList();

            assertThat(outcomes).filteredOn(CaptchaRepository.VerifyResult.MATCHED::equals).hasSize(1);
            assertThat(outcomes).filteredOn(CaptchaRepository.VerifyResult.NOT_FOUND::equals)
                    .hasSize(attempts - 1);
            assertThat(redis.hasKey(valueKey(captchaId))).isFalse();
            assertThat(redis.hasKey(failureKey(captchaId))).isFalse();
        } finally {
            executor.shutdownNow();
            connectionFactory.destroy();
        }
    }

    @Test
    void mismatchBudgetShouldExpireWithTheChallengeAndDeleteAtThreshold() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisCaptchaRepository repository = new RedisCaptchaRepository(redis);
        String captchaId = "20000000000000000000000000000002";
        try {
            repository.save(captchaId, "AbC1", Duration.ofSeconds(5));

            assertThat(repository.verifyAndConsume(captchaId, "wrong", 3, Duration.ofMinutes(1)))
                    .isEqualTo(CaptchaRepository.VerifyResult.MISMATCH);
            long firstTtl = redis.getExpire(failureKey(captchaId), TimeUnit.MILLISECONDS);
            assertThat(firstTtl).isPositive().isLessThanOrEqualTo(5_000L);

            Thread.sleep(100L);
            assertThat(repository.verifyAndConsume(captchaId, "wrong", 3, Duration.ofMinutes(1)))
                    .isEqualTo(CaptchaRepository.VerifyResult.MISMATCH);
            long secondTtl = redis.getExpire(failureKey(captchaId), TimeUnit.MILLISECONDS);
            assertThat(secondTtl).isPositive().isLessThan(firstTtl);

            assertThat(repository.verifyAndConsume(captchaId, "wrong", 3, Duration.ofMinutes(1)))
                    .isEqualTo(CaptchaRepository.VerifyResult.EXHAUSTED);
            assertThat(redis.hasKey(valueKey(captchaId))).isFalse();
            assertThat(redis.hasKey(failureKey(captchaId))).isFalse();
            assertThat(repository.verifyAndConsume(captchaId, "AbC1", 3, Duration.ofMinutes(1)))
                    .isEqualTo(CaptchaRepository.VerifyResult.NOT_FOUND);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void malformedRedisTypeShouldFailClosed() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisCaptchaRepository repository = new RedisCaptchaRepository(redis);
        String captchaId = "30000000000000000000000000000003";
        try {
            redis.opsForHash().put(valueKey(captchaId), "unexpected", "type");

            assertThatThrownBy(() -> repository.verifyAndConsume(
                    captchaId, "AbC1", 3, Duration.ofSeconds(30)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(redis.type(valueKey(captchaId)).code()).isEqualTo("hash");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void verificationKeysShouldShareOneRedisClusterSlot() {
        String captchaId = "40000000000000000000000000000004";

        assertThat(ClusterSlotHashUtil.calculateSlot(valueKey(captchaId)))
                .isEqualTo(ClusterSlotHashUtil.calculateSlot(failureKey(captchaId)));
    }

    private CaptchaRepository.VerifyResult get(Future<CaptchaRepository.VerifyResult> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("captcha verification did not complete", exception);
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

    private static String valueKey(String captchaId) {
        return "captcha:{" + captchaId + "}:value";
    }

    private static String failureKey(String captchaId) {
        return "captcha:{" + captchaId + "}:fail";
    }
}
