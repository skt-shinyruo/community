package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.application.RegistrationCodeMailDeliveryApplicationService;
import com.nowcoder.community.auth.application.port.MailPort;
import com.nowcoder.community.auth.application.port.RegistrationCodeMailDispatcher;
import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisRegistrationCodeRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void typedCapabilitiesShouldOwnTheReplacementDeliveryAndVerificationLifecycle() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(90);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);

            RegistrationCodeRepository.ReplacementLease replacement = repository.tryBeginReplacement(
                            userId,
                            "222222",
                            Duration.ofMinutes(5),
                            Duration.ZERO,
                            Duration.ofMinutes(1)
                    )
                    .orElseThrow();
            RegistrationCodeRepository.DeliveryClaim delivery = repository.claimMailDelivery(
                            userId,
                            replacement.id(),
                            "222222",
                            replacement.id(),
                            Duration.ofMinutes(1),
                            Duration.ZERO
                    )
                    .orElseThrow();

            assertThat(delivery.complete()).isTrue();
            assertThat(delivery.complete()).isFalse();
            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "ACTIVE")
                    .containsEntry("active_code", "222222");

            RegistrationCodeRepository.VerificationResult firstAttempt = repository.claimVerification(
                    userId, "222222", Duration.ofMinutes(1));
            assertThat(firstAttempt).isInstanceOf(RegistrationCodeRepository.VerificationClaim.class);
            RegistrationCodeRepository.VerificationClaim restorable =
                    (RegistrationCodeRepository.VerificationClaim) firstAttempt;
            assertThat(restorable.restore()).isTrue();
            assertThat(restorable.consume()).isFalse();

            RegistrationCodeRepository.VerificationResult secondAttempt = repository.claimVerification(
                    userId, "222222", Duration.ofMinutes(1));
            assertThat(secondAttempt).isInstanceOf(RegistrationCodeRepository.VerificationClaim.class);
            assertThat(((RegistrationCodeRepository.VerificationClaim) secondAttempt).consume()).isTrue();
            assertThat(redis.hasKey(key(userId))).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void cooldownAndAbortShouldPreserveThePreviouslyDeliveredCode() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(1);
        UUID replacementLease = uuid(11);
        UUID wrongLease = uuid(12);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(1),
                    Instant.now().plusSeconds(30),
                    replacementLease
            )).isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    replacementLease
            )).isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.abortReplacement(userId, wrongLease)).isFalse();
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), uuid(13)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING_CONFLICT);

            assertThat(repository.abortReplacement(userId, replacementLease)).isTrue();
            UUID verificationLease = uuid(14);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), verificationLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
            assertThat(repository.restorePending(userId, verificationLease)).isTrue();

            Map<Object, Object> fields = redis.opsForHash().entries(key(userId));
            assertThat(fields)
                    .containsEntry("active_code", "111111")
                    .containsEntry("state", "ACTIVE")
                    .doesNotContainKeys("replacement_code", "replacement_lease_id");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void replacementPromotionAndVerificationCompletionShouldRejectStaleLeases() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(2);
        UUID replacementLease = uuid(21);
        UUID wrongLease = uuid(22);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    replacementLease
            )).isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);

            assertThat(repository.promoteReplacement(userId, wrongLease)).isFalse();
            assertThat(repository.abortReplacement(userId, wrongLease)).isFalse();
            assertThat(repository.promoteReplacement(userId, replacementLease)).isTrue();

            UUID verificationLease = uuid(23);
            assertThat(repository.verifyForConsumption(
                    userId, "222222", Instant.now().plusSeconds(30), verificationLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
            assertThat(repository.consumePending(userId, wrongLease)).isFalse();
            assertThat(repository.restorePending(userId, wrongLease)).isFalse();
            assertThat(repository.consumePending(userId, verificationLease)).isTrue();
            assertThat(repository.verifyForConsumption(
                    userId, "222222", Instant.now().plusSeconds(30), uuid(24)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.NOT_FOUND);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void concurrentReplacementsShouldHaveExactlyOneLeaseOwnerAndAbortShouldRestoreActiveCode() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(3);
        UUID firstLease = uuid(31);
        UUID secondLease = uuid(32);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Instant leaseExpiresAt = Instant.now().plusSeconds(30);

            Future<Attempt> first = executor.submit(() -> attemptReplacement(
                    repository, userId, "222222", firstLease, leaseExpiresAt, ready, start));
            Future<Attempt> second = executor.submit(() -> attemptReplacement(
                    repository, userId, "333333", secondLease, leaseExpiresAt, ready, start));
            ready.await();
            start.countDown();

            List<Attempt> attempts = List.of(first.get(), second.get());
            assertThat(attempts)
                    .filteredOn(attempt -> attempt.result() == RegistrationCodeRepository.IssueResult.ISSUED)
                    .hasSize(1);
            assertThat(attempts)
                    .filteredOn(attempt -> attempt.result() == RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE)
                    .hasSize(1);

            Attempt owner = attempts.stream()
                    .filter(attempt -> attempt.result() == RegistrationCodeRepository.IssueResult.ISSUED)
                    .findFirst()
                    .orElseThrow();
            Attempt rejected = attempts.stream()
                    .filter(attempt -> attempt.result() == RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE)
                    .findFirst()
                    .orElseThrow();
            assertThat(repository.abortReplacement(userId, rejected.leaseId())).isFalse();
            assertThat(repository.abortReplacement(userId, owner.leaseId())).isTrue();

            UUID verificationLease = uuid(33);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), verificationLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
            assertThat(repository.restorePending(userId, verificationLease)).isTrue();
        } finally {
            executor.shutdownNow();
            connectionFactory.destroy();
        }
    }

    @Test
    void expiredVerificationLeaseShouldBeReclaimableWithoutGivingTheOldOwnerWriteAuthority() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(4);
        UUID expiredLease = uuid(41);
        UUID currentLease = uuid(42);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(1), expiredLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), currentLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING_CONFLICT);

            awaitRedisDeadline(redis, userId, "verification_lease_expires_at_ms", Duration.ZERO);

            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), currentLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
            assertThat(repository.restorePending(userId, expiredLease)).isFalse();
            assertThat(repository.consumePending(userId, expiredLease)).isFalse();
            assertThat(repository.restorePending(userId, currentLease)).isTrue();
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void expiredVerificationLeaseShouldNotConsumeWithoutACompetingTakeover() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(43);
        UUID expiredLease = uuid(44);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusMillis(500), expiredLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);

            awaitRedisDeadline(redis, userId, "verification_lease_expires_at_ms", Duration.ZERO);

            assertThat(repository.consumePending(userId, expiredLease)).isFalse();
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), uuid(45)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void exhaustedCodeShouldRemainAsCooldownTombstoneAndBlockImmediateReplacement() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(46);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.verifyForConsumption(
                    userId, "000000", Instant.now().plusSeconds(30), uuid(47)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.MISMATCH);
            assertThat(repository.verifyForConsumption(
                    userId, "000000", Instant.now().plusSeconds(30), uuid(48)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.MISMATCH);
            assertThat(repository.verifyForConsumption(
                    userId, "000000", Instant.now().plusSeconds(30), uuid(49)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS);

            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "EXHAUSTED")
                    .containsEntry("failures", "3")
                    .doesNotContainKeys("active_code", "active_delivery_id");
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), uuid(50)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.TOO_MANY_ATTEMPTS);
            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ofMinutes(1),
                    Instant.now().plusSeconds(30),
                    uuid(51)
            )).isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void mailPreparationShouldFenceDeliveryIdentityAndRecoverTheExactReplacementLease() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(52);
        UUID initialDelivery = uuid(53);
        UUID replacementDelivery = uuid(54);
        try {
            assertThat(repository.issue(
                    userId, "111111", Duration.ofMinutes(5), Duration.ZERO, initialDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.prepareMailDelivery(
                    userId, uuid(55), "111111", null, Instant.now().plusSeconds(30))).isFalse();
            assertThat(repository.prepareMailDelivery(
                    userId, initialDelivery, "111111", null, Instant.now().plusSeconds(30))).isTrue();
            assertThat(repository.completeInitialDelivery(
                    userId, initialDelivery, "111111", Duration.ZERO)).isTrue();

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusMillis(500),
                    replacementDelivery
            )).isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            awaitRedisDeadline(redis, userId, "replacement_lease_expires_at_ms", Duration.ZERO);

            assertThat(repository.prepareMailDelivery(
                    userId,
                    replacementDelivery,
                    "222222",
                    replacementDelivery,
                    Instant.now().plusSeconds(30)
            )).isTrue();
            assertThat(repository.promoteReplacement(userId, replacementDelivery)).isTrue();
            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "ACTIVE")
                    .containsEntry("active_code", "222222")
                    .containsEntry("active_delivery_id", replacementDelivery.toString());
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void initialMailDeliveryLeaseShouldBlockReplacementAndVerification() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(56);
        UUID initialDelivery = uuid(57);
        try {
            assertThat(repository.issue(
                    userId, "111111", Duration.ofMinutes(5), Duration.ZERO, initialDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.prepareMailDelivery(
                    userId, initialDelivery, "111111", null, Instant.now().plusSeconds(30))).isTrue();

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    uuid(58)
            )).isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), uuid(59)))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING_CONFLICT);
            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "PENDING_INITIAL_DELIVERY")
                    .containsEntry("initial_delivery_id", initialDelivery.toString());
            assertThat(repository.completeInitialDelivery(
                    userId, initialDelivery, "111111", Duration.ofSeconds(30))).isTrue();
            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "ACTIVE")
                    .doesNotContainKeys("initial_delivery_id", "initial_delivery_lease_expires_at_ms");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void blockedInitialSmtpShouldHoldRedisOwnershipAgainstResend() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(63);
        UUID initialDelivery = uuid(64);
        CountDownLatch smtpEntered = new CountDownLatch(1);
        CountDownLatch releaseSmtp = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RegistrationProperties properties = new RegistrationProperties();
        properties.getCode().setOperationLeaseSeconds(60);
        MailPort blockingMail = new MailPort() {
            @Override
            public void sendRegistrationCodeMail(String toEmail, String code, String deliveryReference) {
                smtpEntered.countDown();
                try {
                    releaseSmtp.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }

            @Override
            public void sendPasswordResetMail(String toEmail, String resetLink, String deliveryReference) {
                throw new UnsupportedOperationException();
            }
        };
        RegistrationCodeMailDeliveryApplicationService deliveryService =
                new RegistrationCodeMailDeliveryApplicationService(
                        repository, blockingMail, properties, java.time.Clock.systemUTC());
        try {
            assertThat(repository.issue(
                    userId, "111111", Duration.ofMinutes(5), Duration.ZERO, initialDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            Future<RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome> delivery = executor.submit(
                    () -> deliveryService.deliver(new RegistrationCodeMailDispatcher.Delivery(
                            initialDelivery,
                            userId,
                            null,
                            "alice@example.com",
                            "111111",
                            Instant.now().plus(Duration.ofMinutes(5))
                    )));
            assertThat(smtpEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    uuid(65)
            )).isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

            releaseSmtp.countDown();
            assertThat(delivery.get(5, TimeUnit.SECONDS))
                    .isEqualTo(RegistrationCodeMailDeliveryApplicationService.DeliveryOutcome.DELIVERED);
        } finally {
            releaseSmtp.countDown();
            executor.shutdownNow();
            connectionFactory.destroy();
        }
    }

    @Test
    void verificationLeaseShouldBlockResendUntilTheVerifierRestoresOwnership() {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(66);
        UUID verificationLease = uuid(67);
        try {
            assertThat(issue(repository, userId, "111111", Duration.ofMinutes(5), Duration.ZERO))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.verifyForConsumption(
                    userId, "111111", Instant.now().plusSeconds(30), verificationLease))
                    .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.PENDING);

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    uuid(68)
            )).isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

            assertThat(repository.restorePending(userId, verificationLease)).isTrue();
            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    uuid(69)
            )).isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void staleInitialMailOwnerShouldNotCompleteAfterReplacementTakesOwnership() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID userId = uuid(60);
        UUID initialDelivery = uuid(61);
        UUID replacementDelivery = uuid(62);
        try {
            assertThat(repository.issue(
                    userId, "111111", Duration.ofMinutes(5), Duration.ZERO, initialDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.prepareMailDelivery(
                    userId, initialDelivery, "111111", null, Instant.now().plusSeconds(1))).isTrue();
            awaitRedisDeadline(redis, userId, "initial_delivery_lease_expires_at_ms", Duration.ZERO);

            assertThat(repository.beginReplacement(
                    userId,
                    "222222",
                    Duration.ofMinutes(5),
                    Duration.ZERO,
                    Instant.now().plusSeconds(30),
                    replacementDelivery
            )).isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.completeInitialDelivery(
                    userId, initialDelivery, "111111", Duration.ofSeconds(30))).isFalse();
            assertThat(repository.prepareMailDelivery(
                    userId,
                    replacementDelivery,
                    "222222",
                    replacementDelivery,
                    Instant.now().plusSeconds(30)
            )).isTrue();
            assertThat(repository.promoteReplacement(userId, replacementDelivery)).isTrue();
            assertThat(redis.opsForHash().entries(key(userId)))
                    .containsEntry("state", "ACTIVE")
                    .containsEntry("active_code", "222222");
        } finally {
            connectionFactory.destroy();
        }
    }

    @Test
    void deliveryValidityMarginShouldBeEnforcedByRedisBeforeAndAfterSmtp() throws Exception {
        LettuceConnectionFactory connectionFactory = connectionFactory();
        StringRedisTemplate redis = redisTemplate(connectionFactory);
        RedisRegistrationCodeRepository repository = repository(redis);
        UUID tooShortUser = uuid(73);
        UUID expiringUser = uuid(74);
        UUID tooShortDelivery = uuid(75);
        UUID expiringDelivery = uuid(76);
        try {
            assertThat(repository.issue(
                    tooShortUser, "111111", Duration.ofSeconds(1), Duration.ZERO, tooShortDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.prepareMailDelivery(
                    tooShortUser,
                    tooShortDelivery,
                    "111111",
                    null,
                    Instant.now().plusSeconds(30),
                    Duration.ofSeconds(2)
            )).isFalse();

            assertThat(repository.issue(
                    expiringUser, "222222", Duration.ofSeconds(2), Duration.ZERO, expiringDelivery))
                    .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
            assertThat(repository.prepareMailDelivery(
                    expiringUser,
                    expiringDelivery,
                    "222222",
                    null,
                    Instant.now().plusSeconds(30),
                    Duration.ofSeconds(1)
            )).isTrue();
            awaitRedisDeadline(redis, expiringUser, "active_expires_at_ms", Duration.ofSeconds(1));

            assertThat(repository.completeInitialDelivery(
                    expiringUser, expiringDelivery, "222222", Duration.ofSeconds(1))).isFalse();
        } finally {
            connectionFactory.destroy();
        }
    }

    private static Attempt attemptReplacement(
            RedisRegistrationCodeRepository repository,
            UUID userId,
            String code,
            UUID leaseId,
            Instant leaseExpiresAt,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        RegistrationCodeRepository.IssueResult result = repository.beginReplacement(
                userId, code, Duration.ofMinutes(5), Duration.ZERO, leaseExpiresAt, leaseId);
        return new Attempt(leaseId, result);
    }

    private static RegistrationCodeRepository.IssueResult issue(
            RedisRegistrationCodeRepository repository,
            UUID userId,
            String code,
            Duration ttl,
            Duration cooldown
    ) {
        return repository.issue(userId, code, ttl, cooldown, UUID.randomUUID());
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

    private static void awaitRedisDeadline(
            StringRedisTemplate redis,
            UUID userId,
            String deadlineField,
            Duration beforeDeadline
    ) {
        Object rawDeadline = redis.opsForHash().get(key(userId), deadlineField);
        assertThat(rawDeadline).as(deadlineField).isNotNull();
        long thresholdMs = Long.parseLong(String.valueOf(rawDeadline))
                - Math.max(0L, beforeDeadline.toMillis());
        await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(20))
                .untilAsserted(() -> assertThat(redisTimeMillis(redis)).isGreaterThanOrEqualTo(thresholdMs));
    }

    private static Long redisTimeMillis(StringRedisTemplate redis) {
        return redis.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().time(TimeUnit.MILLISECONDS));
    }

    private static String key(UUID userId) {
        return "auth:regcode:v2:{" + userId + "}";
    }

    private static RedisRegistrationCodeRepository repository(StringRedisTemplate redis) {
        return new RedisRegistrationCodeRepository(
                redis, new RegistrationProperties(), java.time.Clock.systemUTC());
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }

    private record Attempt(UUID leaseId, RegistrationCodeRepository.IssueResult result) {
    }
}
