package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisRegistrationCodeRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void legacyKeyAbsent() {
        lenient().when(redisTemplate.execute(any(RedisScript.class), any(List.class)))
                .thenReturn(null);
    }

    @Test
    void issueShouldUseAClusterTaggedHashKeyAndStructuredFields() {
        UUID userId = uuid(7);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("222222"),
                eq("300000"),
                eq("60000"),
                any(String.class)))
                .thenReturn("ISSUED");
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        assertThat(repository.issue(
                userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1), uuid(70)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(keys(userId)),
                eq("222222"),
                eq("300000"),
                eq("60000"),
                any(String.class)
        );
        String script = ((DefaultRedisScript<?>) scriptCaptor.getValue()).getScriptAsString();
        assertThat(script)
                .contains(
                        "redis.call('TIME')",
                        "HSET",
                        "active_code",
                        "active_expires_at_ms",
                        "issued_at_ms"
                )
                .doesNotContain("KEYS[2]", "System.currentTimeMillis");
    }

    @Test
    void replacementLifecycleShouldCarryTheSameLeaseThroughEveryMutation() {
        UUID userId = uuid(8);
        UUID leaseId = uuid(81);
        Instant leaseExpiresAt = Instant.now().plusSeconds(60);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("333333"),
                eq("300000"),
                eq("0"),
                eq(leaseId.toString()),
                any(String.class)))
                .thenReturn("ISSUED");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(leaseId.toString()),
                eq("0")))
                .thenReturn(1L);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(leaseId.toString())))
                .thenReturn(1L);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        assertThat(repository.beginReplacement(
                userId, "333333", Duration.ofMinutes(5), Duration.ZERO, leaseExpiresAt, leaseId))
                .isEqualTo(RegistrationCodeRepository.IssueResult.ISSUED);
        assertThat(repository.promoteReplacement(userId, leaseId)).isTrue();
        assertThat(repository.abortReplacement(userId, leaseId)).isTrue();

        ArgumentCaptor<RedisScript<String>> beginScriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                beginScriptCaptor.capture(),
                eq(keys(userId)),
                eq("333333"),
                eq("300000"),
                eq("0"),
                eq(leaseId.toString()),
                any(String.class)
        );
        String script = ((DefaultRedisScript<?>) beginScriptCaptor.getValue()).getScriptAsString();
        assertThat(script)
                .contains("replacement_lease_id", "replacement_lease_expires_at_ms", "PENDING_REPLACEMENT")
                .doesNotContain("gmatch");
    }

    @Test
    void verificationLifecycleShouldBeFencedByLease() {
        UUID userId = uuid(9);
        UUID leaseId = uuid(91);
        Instant leaseExpiresAt = Instant.now().plusSeconds(60);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("222222"),
                eq("3"),
                any(String.class),
                eq(leaseId.toString()),
                any(String.class)))
                .thenReturn("PENDING");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(leaseId.toString())))
                .thenReturn(1L, 1L);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        assertThat(repository.verifyForConsumption(userId, "222222", leaseExpiresAt, leaseId))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.PENDING);
        assertThat(repository.consumePending(userId, leaseId)).isTrue();
        assertThat(repository.restorePending(userId, leaseId)).isTrue();
    }

    @Test
    void invalidOrUnknownScriptOutcomesShouldFailClosed() {
        UUID userId = uuid(10);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("222222"),
                eq("300000"),
                eq("60000"),
                any(String.class)))
                .thenReturn("unexpected");
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        assertThat(repository.issue(
                userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1), uuid(100)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);
        assertThat(repository.verifyForConsumption(
                userId, "222222", Instant.now().minusSeconds(1), UUID.randomUUID()))
                .isEqualTo(RegistrationCodeRepository.VerifyResult.NOT_FOUND);
    }

    @Test
    void legacyMigrationShouldSnapshotImportThenDeleteOnlyTheUnchangedLegacyValue() {
        UUID userId = uuid(12);
        long issuedAtMs = System.currentTimeMillis() - 1_000L;
        long expiresAtMs = issuedAtMs + 300_000L;
        String raw = "111111|" + expiresAtMs + "|0|" + issuedAtMs + "|ACTIVE|||";
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(legacyKey(userId)))))
                .thenReturn(List.of(raw, 60_000L));
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("111111"),
                eq(Long.toString(expiresAtMs)),
                eq("0"),
                eq(Long.toString(issuedAtMs)),
                eq("60000")))
                .thenReturn("IMPORTED");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(legacyKey(userId))),
                eq(raw)))
                .thenReturn(1L);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("222222"),
                eq("300000"),
                eq("60000"),
                any(String.class)))
                .thenReturn("COOLDOWN_ACTIVE");
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        assertThat(repository.issue(
                userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1), uuid(120)))
                .isEqualTo(RegistrationCodeRepository.IssueResult.COOLDOWN_ACTIVE);

        ArgumentCaptor<RedisScript<List>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(legacyKey(userId))));
        String script = ((DefaultRedisScript<?>) scriptCaptor.getValue()).getScriptAsString();
        assertThat(script).contains(
                "redis.call('GET', KEYS[1])",
                "redis.call('PTTL', KEYS[1])"
        ).doesNotContain("redis.call('DEL', KEYS[1])");
        ArgumentCaptor<RedisScript<Long>> deleteScriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                deleteScriptCaptor.capture(),
                eq(List.of(legacyKey(userId))),
                eq(raw)
        );
        assertThat(((DefaultRedisScript<?>) deleteScriptCaptor.getValue()).getScriptAsString())
                .contains("current ~= ARGV[1]", "redis.call('DEL', KEYS[1])");
        verify(redisTemplate, never()).hasKey(legacyKey(userId));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void legacyMigrationShouldRetainLegacyValueWhenImportFailsAmbiguously() {
        UUID userId = uuid(13);
        long issuedAtMs = System.currentTimeMillis() - 1_000L;
        long expiresAtMs = issuedAtMs + 300_000L;
        String raw = "111111|" + expiresAtMs + "|0|" + issuedAtMs + "|ACTIVE|||";
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of(legacyKey(userId)))))
                .thenReturn(List.of(raw, 60_000L));
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("111111"),
                eq(Long.toString(expiresAtMs)),
                eq("0"),
                eq(Long.toString(issuedAtMs)),
                eq("60000")))
                .thenThrow(new IllegalStateException("ambiguous redis timeout"));
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.issue(
                        userId, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1), uuid(130)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ambiguous redis timeout");

        verify(redisTemplate, never()).execute(
                any(RedisScript.class),
                eq(List.of(legacyKey(userId))),
                eq(raw)
        );
    }

    @Test
    void deleteShouldRemoveBothVersionedAndLegacyKeys() {
        UUID userId = uuid(11);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        repository.delete(userId);

        verify(redisTemplate).delete(key(userId));
        verify(redisTemplate).delete(legacyKey(userId));
    }

    private static String key(UUID userId) {
        return "auth:regcode:v2:{" + userId + "}";
    }

    private static RedisRegistrationCodeRepository repository(StringRedisTemplate redisTemplate) {
        return new RedisRegistrationCodeRepository(
                redisTemplate, new RegistrationProperties(), java.time.Clock.systemUTC());
    }

    private static String legacyKey(UUID userId) {
        return "auth:regcode:" + userId;
    }

    private static List<String> keys(UUID userId) {
        return List.of(key(userId));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
