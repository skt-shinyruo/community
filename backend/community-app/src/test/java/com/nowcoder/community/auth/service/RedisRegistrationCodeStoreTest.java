package com.nowcoder.community.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisRegistrationCodeStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void issueShouldExposeIssueContractForUserCodeTtlAndCooldown() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq(Long.toString(Duration.ofMinutes(5).toMillis())),
                eq(Long.toString(Duration.ofMinutes(1).toMillis()))))
                .thenReturn("ISSUED");

        RedisRegistrationCodeStore store = new RedisRegistrationCodeStore(redisTemplate);

        assertThat(store.issue(7, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);
    }

    @Test
    void issueShouldSurfaceCooldownOutcomeFromRedisScript() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq(Long.toString(Duration.ofMinutes(5).toMillis())),
                eq(Long.toString(Duration.ofMinutes(1).toMillis()))))
                .thenReturn("COOLDOWN_ACTIVE");

        RedisRegistrationCodeStore store = new RedisRegistrationCodeStore(redisTemplate);

        assertThat(store.issue(7, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.COOLDOWN_ACTIVE);
    }

    @Test
    void issueScriptShouldTreatLegacyPayloadsWithoutIssuedTimestampAsNoCooldown() {
        RedisRegistrationCodeStore store = new RedisRegistrationCodeStore(redisTemplate);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq(Long.toString(Duration.ofMinutes(5).toMillis())),
                eq(Long.toString(Duration.ofMinutes(1).toMillis()))))
                .thenReturn("ISSUED");

        assertThat(store.issue(7, "222222", Duration.ofMinutes(5), Duration.ofMinutes(1)))
                .isEqualTo(RegistrationCodeStore.IssueResult.ISSUED);

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq(Long.toString(Duration.ofMinutes(5).toMillis())),
                eq(Long.toString(Duration.ofMinutes(1).toMillis()))
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("issued = 0");
    }

    @Test
    void verifyAndConsumeShouldExposeVerificationOutcomesThroughPublicContract() {
        RedisRegistrationCodeStore store = new RedisRegistrationCodeStore(redisTemplate);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq("3")))
                .thenReturn("SUCCESS");
        assertThat(store.verifyAndConsume(7, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.SUCCESS);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("111111"),
                any(String.class),
                eq("3")))
                .thenReturn("MISMATCH");
        assertThat(store.verifyAndConsume(7, "111111"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.MISMATCH);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("333333"),
                any(String.class),
                eq("3")))
                .thenReturn("TOO_MANY_ATTEMPTS");
        assertThat(store.verifyAndConsume(7, "333333"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.TOO_MANY_ATTEMPTS);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq("3")))
                .thenReturn("EXPIRED");
        assertThat(store.verifyAndConsume(7, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.EXPIRED);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("auth:regcode:7")),
                eq("222222"),
                any(String.class),
                eq("3")))
                .thenReturn("NOT_FOUND");
        assertThat(store.verifyAndConsume(7, "222222"))
                .isEqualTo(RegistrationCodeStore.VerifyResult.NOT_FOUND);
    }
}
