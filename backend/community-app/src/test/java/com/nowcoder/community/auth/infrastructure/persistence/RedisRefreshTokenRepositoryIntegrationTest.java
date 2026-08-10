package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.RefreshTokenRepository;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

@Testcontainers
class RedisRefreshTokenRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000042");
    private static final long SECURITY_VERSION = 17L;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Test
    void expiredLeaseShouldBeReclaimedAndFenceItsFormerOwner() throws InterruptedException {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String token = "refresh-lease-original";
            String staleReplacement = "refresh-lease-stale-replacement";
            UUID staleLease = UUID.fromString("00000000-0000-7000-8000-000000000101");
            UUID currentLease = UUID.fromString("00000000-0000-7000-8000-000000000102");
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(token, USER_ID, "family-lease", SECURITY_VERSION, expiresAt);

            assertThat(repository.beginRotation(token, Instant.now().plusMillis(250), staleLease))
                    .isNotNull();
            TimeUnit.MILLISECONDS.sleep(300);
            RefreshTokenRepository.StoredRefreshToken reclaimed = repository.beginRotation(
                    token,
                    Instant.now().plusSeconds(30),
                    currentLease
            );

            assertThat(reclaimed).isNotNull();
            assertThat(reclaimed.rotationLeaseId()).isEqualTo(currentLease);
            assertThat(repository.rollbackPendingRotation(token, staleLease)).isFalse();
            assertThat(repository.finishRotation(
                    token,
                    staleReplacement,
                    USER_ID,
                    "family-lease",
                    SECURITY_VERSION,
                    expiresAt,
                    staleLease
            )).isFalse();
            assertThat(repository.find(staleReplacement)).isNull();
            assertThat(repository.rollbackPendingRotation(token, currentLease)).isTrue();
            assertThat(repository.find(token)).isNotNull();
        }
    }

    @Test
    void finishRotationShouldAtomicallyConsumeOldTokenAndActivateReplacement() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String original = "refresh-atomic-original";
            String replacement = "refresh-atomic-replacement";
            String familyId = "family-atomic";
            UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000201");
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(original, USER_ID, familyId, SECURITY_VERSION, expiresAt);
            assertThat(repository.beginRotation(original, Instant.now().plusSeconds(30), lease))
                    .isNotNull();

            assertThat(repository.finishRotation(
                    original,
                    replacement,
                    USER_ID,
                    familyId,
                    SECURITY_VERSION,
                    expiresAt,
                    lease
            )).isTrue();

            assertThat(repository.find(original)).isNull();
            assertThat(repository.findRevoked(original))
                    .isNotNull()
                    .satisfies(consumed -> {
                        assertThat(consumed.userId()).isEqualTo(USER_ID);
                        assertThat(consumed.familyId()).isEqualTo(familyId);
                    });
            assertThat(repository.find(replacement))
                    .isNotNull()
                    .satisfies(active -> {
                        assertThat(active.userId()).isEqualTo(USER_ID);
                        assertThat(active.familyId()).isEqualTo(familyId);
                        assertThat(active.securityVersionAtIssue()).isEqualTo(SECURITY_VERSION);
                    });
            assertThat(repository.finishRotation(
                    original,
                    "refresh-atomic-second-replacement",
                    USER_ID,
                    familyId,
                    SECURITY_VERSION,
                    expiresAt,
                    lease
            )).isFalse();
        }
    }

    @Test
    void familyRevocationMarkerShouldInvalidateExistingAndRejectFutureTokens() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String familyId = "family-revoked";
            String first = "refresh-family-first";
            String second = "refresh-family-second";
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(first, USER_ID, familyId, SECURITY_VERSION, expiresAt);
            repository.store(second, USER_ID, familyId, SECURITY_VERSION, expiresAt);

            repository.revokeFamily(familyId);

            assertThat(repository.find(first)).isNull();
            assertThat(repository.find(second)).isNull();
            assertThat(repository.beginRotation(
                    first,
                    Instant.now().plusSeconds(30),
                    UUID.randomUUID()
            )).isNull();
            assertThatThrownBy(() -> repository.store(
                    "refresh-family-late",
                    USER_ID,
                    familyId,
                    SECURITY_VERSION,
                    expiresAt
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("refresh token family");
        }
    }

    @Test
    void familyRevocationShouldKeepTheLongestMarkerFamilyOrConfiguredTtl() {
        try (RedisHarness harness = harness()) {
            String markerFamily = "family-revocation-marker-ttl";
            String markerRevokedKey = familyRevokedKey(markerFamily);
            harness.redisTemplate().opsForValue().set(markerRevokedKey, "1", 30, TimeUnit.SECONDS);

            harness.repository(1).revokeFamily(markerFamily);

            assertThat(harness.redisTemplate().getExpire(markerRevokedKey, TimeUnit.MILLISECONDS))
                    .isNotNull()
                    .isGreaterThan(25_000L);

            String indexedFamily = "family-revocation-index-ttl";
            String indexedFamilyKey = familyKey(indexedFamily);
            String indexedRevokedKey = familyRevokedKey(indexedFamily);
            harness.redisTemplate().opsForSet().add(indexedFamilyKey, "token-id");
            harness.redisTemplate().expire(indexedFamilyKey, 30, TimeUnit.SECONDS);
            harness.redisTemplate().opsForValue().set(indexedRevokedKey, "1", 1, TimeUnit.SECONDS);

            harness.repository(1).revokeFamily(indexedFamily);

            assertThat(harness.redisTemplate().getExpire(indexedRevokedKey, TimeUnit.MILLISECONDS))
                    .isNotNull()
                    .isGreaterThan(25_000L);

            String configuredFamily = "family-revocation-configured-ttl";
            String configuredRevokedKey = familyRevokedKey(configuredFamily);
            harness.redisTemplate().opsForValue().set(configuredRevokedKey, "1", 1, TimeUnit.SECONDS);

            harness.repository(30).revokeFamily(configuredFamily);

            assertThat(harness.redisTemplate().getExpire(configuredRevokedKey, TimeUnit.MILLISECONDS))
                    .isNotNull()
                    .isGreaterThan(25_000L);
        }
    }

    @Test
    void storeShouldRejectARefreshTokenThatAlreadyHasAnActiveRecord() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String token = "refresh-active-collision";
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(token, USER_ID, "family-active-original", SECURITY_VERSION, expiresAt);

            assertThatThrownBy(() -> repository.store(
                    token,
                    USER_ID,
                    "family-active-replacement",
                    SECURITY_VERSION + 1,
                    expiresAt
            )).isInstanceOf(IllegalStateException.class);

            assertThat(repository.find(token)).isNotNull()
                    .extracting(RefreshTokenRepository.StoredRefreshToken::familyId)
                    .isEqualTo("family-active-original");
            assertThat(harness.redisTemplate().hasKey(familyKey("family-active-replacement"))).isFalse();
        }
    }

    @Test
    void storeShouldRejectARefreshTokenThatOnlyHasARevokedRecord() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String token = "refresh-revoked-collision";
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(token, USER_ID, "family-revoked-original", SECURITY_VERSION, expiresAt);
            repository.revoke(token);

            assertThatThrownBy(() -> repository.store(
                    token,
                    USER_ID,
                    "family-revoked-replacement",
                    SECURITY_VERSION + 1,
                    expiresAt
            )).isInstanceOf(IllegalStateException.class);

            assertThat(repository.find(token)).isNull();
            assertThat(repository.findRevoked(token)).isNotNull()
                    .extracting(RefreshTokenRepository.RevokedRefreshToken::familyId)
                    .isEqualTo("family-revoked-original");
            assertThat(harness.redisTemplate().hasKey(familyKey("family-revoked-replacement"))).isFalse();
        }
    }

    @Test
    void logoutShouldRevokeFamilyWhilePresentedTokenIsPending() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String token = "refresh-logout-pending";
            String replacement = "refresh-logout-replacement";
            String familyId = "family-logout-pending";
            UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000301");
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(token, USER_ID, familyId, SECURITY_VERSION, expiresAt);
            assertThat(repository.beginRotation(token, Instant.now().plusSeconds(30), lease))
                    .isNotNull();

            assertThat(repository.revokeFamilyByPresentedToken(token)).isTrue();

            assertThat(repository.find(token)).isNull();
            assertThat(repository.beginRotation(
                    token,
                    Instant.now().plusSeconds(30),
                    UUID.randomUUID()
            )).isNull();
            assertThat(repository.finishRotation(
                    token,
                    replacement,
                    USER_ID,
                    familyId,
                    SECURITY_VERSION,
                    expiresAt,
                    lease
            )).isFalse();
            assertThat(repository.find(replacement)).isNull();
        }
    }

    @Test
    void finishRotationShouldRejectAReplacementThatOnlyHasARevokedRecord() {
        try (RedisHarness harness = harness()) {
            RedisRefreshTokenRepository repository = harness.repository();
            String original = "refresh-replacement-revoked-original";
            String replacement = "refresh-replacement-revoked-candidate";
            String familyId = "family-replacement-revoked";
            Instant expiresAt = Instant.now().plusSeconds(600);
            UUID lease = UUID.fromString("00000000-0000-7000-8000-000000000401");

            repository.store(replacement, USER_ID, familyId, SECURITY_VERSION, expiresAt);
            repository.revoke(replacement);
            repository.store(original, USER_ID, familyId, SECURITY_VERSION, expiresAt);
            assertThat(repository.beginRotation(original, Instant.now().plusSeconds(30), lease))
                    .isNotNull();

            assertThat(repository.finishRotation(
                    original,
                    replacement,
                    USER_ID,
                    familyId,
                    SECURITY_VERSION,
                    expiresAt,
                    lease
            )).isFalse();

            assertThat(repository.find(original)).isNull();
            assertThat(repository.findRevoked(original)).isNull();
            assertThat(repository.find(replacement)).isNull();
            assertThat(repository.findRevoked(replacement)).isNotNull();
            assertThat(repository.rollbackPendingRotation(original, lease)).isTrue();
            assertThat(repository.find(original)).isNotNull();
        }
    }

    @Test
    void rotationLeaseShouldRejectAnAbsoluteDeadlineThatPassesWhileQueued() {
        try (RedisHarness harness = harness()) {
            AtomicBoolean pauseNextScript = new AtomicBoolean();
            StringRedisTemplate pausedRedis = spy(harness.redisTemplate());
            doAnswer(invocation -> {
                if (pauseNextScript.getAndSet(false)) {
                    harness.pauseAllCommands(900);
                }
                return invocation.callRealMethod();
            }).when(pausedRedis).execute(any(), anyList(), any(Object[].class));

            RedisRefreshTokenRepository repository = harness.repository(pausedRedis, 600);
            String original = "refresh-lease-server-time";
            String familyId = "family-lease-server-time";
            UUID lease = UUID.fromString("00000000-0000-0000-0000-000000000402");
            Instant expiresAt = Instant.now().plusSeconds(600);
            repository.store(original, USER_ID, familyId, SECURITY_VERSION, expiresAt);

            pauseNextScript.set(true);
            long startedAtNanos = System.nanoTime();
            assertThat(repository.beginRotation(
                    original,
                    Instant.now().plusMillis(500),
                    lease
            )).isNull();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos))
                    .isGreaterThanOrEqualTo(750L);

            assertThat(repository.find(original)).isNotNull();
        }
    }

    private static RedisHarness harness() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return new RedisHarness(connectionFactory, redisTemplate);
    }

    private static String familyKey(String familyId) {
        return "auth:refresh:{auth-refresh}:family:" + familyId;
    }

    private static String familyRevokedKey(String familyId) {
        return "auth:refresh:{auth-refresh}:family-revoked:" + familyId;
    }

    private record RedisHarness(
            LettuceConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate
    ) implements AutoCloseable {

        private RedisRefreshTokenRepository repository() {
            return repository(600);
        }

        private RedisRefreshTokenRepository repository(long ttlSeconds) {
            return repository(redisTemplate, ttlSeconds);
        }

        private RedisRefreshTokenRepository repository(StringRedisTemplate template, long ttlSeconds) {
            JwtProperties properties = new JwtProperties();
            properties.setRefreshTokenTtlSeconds(ttlSeconds);
            return new RedisRefreshTokenRepository(
                    template,
                    new JacksonJsonCodec(JsonMappers.standard()),
                    properties,
                    java.time.Clock.systemUTC()
            );
        }

        private void pauseAllCommands(long millis) {
            redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                    "CLIENT",
                    "PAUSE".getBytes(StandardCharsets.UTF_8),
                    Long.toString(millis).getBytes(StandardCharsets.UTF_8),
                    "ALL".getBytes(StandardCharsets.UTF_8)
            ));
        }

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
