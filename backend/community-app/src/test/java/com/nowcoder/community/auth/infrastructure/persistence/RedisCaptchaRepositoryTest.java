package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.CaptchaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCaptchaRepositoryTest {

    private static final String CAPTCHA_ID = "0123456789abcdef0123456789abcdef";
    private static final List<String> CAPTCHA_KEYS = List.of(
            "captcha:{" + CAPTCHA_ID + "}:value",
            "captcha:{" + CAPTCHA_ID + "}:fail"
    );

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void verifyAndConsumeShouldExposeVerificationOutcomeFromRedisScript() {
        RedisCaptchaRepository store = new RedisCaptchaRepository(redisTemplate);

        when(redisTemplate.execute(any(RedisScript.class), eq(CAPTCHA_KEYS),
                eq("AbC1"), eq("3"), eq("60000")))
                .thenReturn("MATCHED");
        assertThat(store.verifyAndConsume(CAPTCHA_ID, "AbC1", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.MATCHED);

        when(redisTemplate.execute(any(RedisScript.class), eq(CAPTCHA_KEYS),
                eq("bad"), eq("3"), eq("60000")))
                .thenReturn("MISMATCH");
        assertThat(store.verifyAndConsume(CAPTCHA_ID, "bad", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.MISMATCH);

        when(redisTemplate.execute(any(RedisScript.class), eq(CAPTCHA_KEYS),
                eq("exhausted"), eq("3"), eq("60000")))
                .thenReturn("EXHAUSTED");
        assertThat(store.verifyAndConsume(CAPTCHA_ID, "exhausted", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.EXHAUSTED);

        when(redisTemplate.execute(any(RedisScript.class), eq(CAPTCHA_KEYS),
                eq("gone"), eq("3"), eq("60000")))
                .thenReturn("NOT_FOUND");
        assertThat(store.verifyAndConsume(CAPTCHA_ID, "gone", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.NOT_FOUND);
    }

    @Test
    void verifyAndConsumeScriptShouldDeleteCaptchaAndFailureCounterAtomically() {
        RedisCaptchaRepository store = new RedisCaptchaRepository(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(CAPTCHA_KEYS),
                eq("AbC1"), eq("3"), eq("60000")))
                .thenReturn("MATCHED");

        assertThat(store.verifyAndConsume(CAPTCHA_ID, "AbC1", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.MATCHED);

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(CAPTCHA_KEYS),
                eq("AbC1"), eq("3"), eq("60000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("string.upper");
        assertThat(script.getScriptAsString()).contains("redis.call('incr', KEYS[2])");
        assertThat(script.getScriptAsString()).contains("failures >= tonumber(ARGV[2])");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[2])");
    }

    @Test
    void incrementFailuresShouldUseAtomicExpireScript() {
        RedisCaptchaRepository store = new RedisCaptchaRepository(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:{cid}:fail")), eq("60000")))
                .thenReturn(1L);

        assertThat(store.incrementFailures("cid", Duration.ofSeconds(60))).isEqualTo(1);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("captcha:{cid}:fail")), eq("60000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire'");
    }

    @Test
    void verifyAndConsumeShouldRejectExternalHashTagInputWithoutExecutingLua() {
        RedisCaptchaRepository store = new RedisCaptchaRepository(redisTemplate);

        assertThat(store.verifyAndConsume("}x", "AbC1", 3, Duration.ofSeconds(60)))
                .isEqualTo(CaptchaRepository.VerifyResult.NOT_FOUND);

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
    }
}
