package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.config.RegistrationProperties;
import com.nowcoder.community.auth.domain.repository.RegistrationCodeRepository;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RedisRegistrationCodeRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

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
    void replacementLeaseShouldOwnItsAbortTransition() {
        UUID userId = uuid(8);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("333333"),
                eq("300000"),
                eq("0"),
                any(String.class),
                any(String.class)))
                .thenReturn("ISSUED");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                any(String.class)))
                .thenReturn(1L);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        RegistrationCodeRepository.ReplacementLease lease = repository.tryBeginReplacement(
                        userId, "333333", Duration.ofMinutes(5), Duration.ZERO, Duration.ofMinutes(1))
                .orElseThrow();

        assertThat(lease.abort()).isTrue();
        assertThat(lease.abort()).isFalse();

        ArgumentCaptor<RedisScript<String>> beginScriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> leaseIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
                beginScriptCaptor.capture(),
                eq(keys(userId)),
                eq("333333"),
                eq("300000"),
                eq("0"),
                leaseIdCaptor.capture(),
                any(String.class)
        );
        assertThat(leaseIdCaptor.getValue()).isEqualTo(lease.id().toString());
        verify(redisTemplate).execute(any(RedisScript.class), eq(keys(userId)), eq(lease.id().toString()));
        String script = ((DefaultRedisScript<?>) beginScriptCaptor.getValue()).getScriptAsString();
        assertThat(script)
                .contains("replacement_lease_id", "replacement_lease_expires_at_ms", "PENDING_REPLACEMENT")
                .doesNotContain("gmatch");
    }

    @Test
    void deliveryClaimShouldRecoverItsLeaseBeforeRetryingCompletion() {
        UUID userId = uuid(85);
        UUID deliveryId = uuid(86);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(deliveryId.toString()),
                eq("333333"),
                eq(deliveryId.toString()),
                any(String.class),
                eq("120000")))
                .thenReturn(1L, 1L);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(deliveryId.toString()),
                eq("120000")))
                .thenReturn(0L, 1L);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        RegistrationCodeRepository.DeliveryClaim claim = repository.claimMailDelivery(
                        userId,
                        deliveryId,
                        "333333",
                        deliveryId,
                        Duration.ofMinutes(2),
                        Duration.ofMinutes(2)
                )
                .orElseThrow();

        assertThat(claim.complete()).isTrue();
        assertThat(claim.complete()).isFalse();
        verify(redisTemplate, times(2)).execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(deliveryId.toString()),
                eq("333333"),
                eq(deliveryId.toString()),
                any(String.class),
                eq("120000"));
        verify(redisTemplate, times(2)).execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(deliveryId.toString()),
                eq("120000"));
    }

    @Test
    void verificationClaimShouldFenceAndAllowOnlyOneTerminalTransition() {
        UUID userId = uuid(9);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq("222222"),
                eq("3"),
                any(String.class),
                any(String.class),
                any(String.class)))
                .thenReturn("PENDING");
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(keys(userId)),
                any(String.class)))
                .thenReturn(1L);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        RegistrationCodeRepository.VerificationResult result = repository.claimVerification(
                userId, "222222", Duration.ofMinutes(1));

        assertThat(result).isInstanceOf(RegistrationCodeRepository.VerificationClaim.class);
        RegistrationCodeRepository.VerificationClaim claim =
                (RegistrationCodeRepository.VerificationClaim) result;
        assertThat(claim.consume()).isTrue();
        assertThat(claim.restore()).isFalse();

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> verificationLeaseCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(keys(userId)),
                eq("222222"),
                eq("3"),
                any(String.class),
                verificationLeaseCaptor.capture(),
                any(String.class));
        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(keys(userId)),
                eq(verificationLeaseCaptor.getValue()));
        String script = ((DefaultRedisScript<?>) scriptCaptor.getValue()).getScriptAsString();
        assertThat(script).contains(
                "local registrationCodeKey = KEYS[1]",
                "local submittedCode = ARGV[1]",
                "local verificationLeaseId = ARGV[4]",
                "local storedCode = storedValues[1]",
                "local initialDeliveryLeaseExpiresAtMs = tonumber(storedValues[10])",
                "if storedCode == submittedCode then"
        );
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
                .isEqualTo(RedisRegistrationCodeRepository.VerifyResult.NOT_FOUND);
    }

    @Test
    void deleteShouldRemoveVersionedKey() {
        UUID userId = uuid(11);
        RedisRegistrationCodeRepository repository = repository(redisTemplate);

        repository.delete(userId);

        verify(redisTemplate).delete(key(userId));
    }

    private static String key(UUID userId) {
        return "auth:regcode:v2:{" + userId + "}";
    }

    private static RedisRegistrationCodeRepository repository(StringRedisTemplate redisTemplate) {
        return new RedisRegistrationCodeRepository(
                redisTemplate, new RegistrationProperties(), java.time.Clock.systemUTC());
    }

    private static List<String> keys(UUID userId) {
        return List.of(key(userId));
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
