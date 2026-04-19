package com.nowcoder.community.auth.service;

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
class RedisCaptchaStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void verifyAndConsumeShouldExposeVerificationOutcomeFromRedisScript() {
        RedisCaptchaStore store = new RedisCaptchaStore(redisTemplate);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:cid", "captcha:fail:cid")), eq("AbC1")))
                .thenReturn("MATCHED");
        assertThat(store.verifyAndConsume("cid", "AbC1")).isEqualTo(CaptchaStore.VerifyResult.MATCHED);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:cid", "captcha:fail:cid")), eq("bad")))
                .thenReturn("MISMATCH");
        assertThat(store.verifyAndConsume("cid", "bad")).isEqualTo(CaptchaStore.VerifyResult.MISMATCH);

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:cid", "captcha:fail:cid")), eq("gone")))
                .thenReturn("NOT_FOUND");
        assertThat(store.verifyAndConsume("cid", "gone")).isEqualTo(CaptchaStore.VerifyResult.NOT_FOUND);
    }

    @Test
    void verifyAndConsumeScriptShouldDeleteCaptchaAndFailureCounterAtomically() {
        RedisCaptchaStore store = new RedisCaptchaStore(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:cid", "captcha:fail:cid")), eq("AbC1")))
                .thenReturn("MATCHED");

        assertThat(store.verifyAndConsume("cid", "AbC1")).isEqualTo(CaptchaStore.VerifyResult.MATCHED);

        ArgumentCaptor<RedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("captcha:cid", "captcha:fail:cid")), eq("AbC1"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("string.upper");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[1])");
        assertThat(script.getScriptAsString()).contains("redis.call('del', KEYS[2])");
    }

    @Test
    void incrementFailuresShouldUseAtomicExpireScript() {
        RedisCaptchaStore store = new RedisCaptchaStore(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("captcha:fail:cid")), eq("60000")))
                .thenReturn(1L);

        assertThat(store.incrementFailures("cid", Duration.ofSeconds(60))).isEqualTo(1);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("captcha:fail:cid")), eq("60000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire'");
    }
}
