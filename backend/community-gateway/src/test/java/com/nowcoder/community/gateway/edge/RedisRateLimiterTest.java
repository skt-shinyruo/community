package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    @Test
    void allowShouldUseAtomicWindowScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("gateway:rate-limit:principal:alice:/limited")),
                eq("30000")))
                .thenReturn(1L);

        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(2);
        policy.setWindow(Duration.ofSeconds(30));

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

        assertThat(limiter.allow("principal:alice:/limited", policy)).isTrue();

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("gateway:rate-limit:principal:alice:/limited")), eq("30000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire'");
    }

    @Test
    void allowShouldRejectWhenCountExceedsLimit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("gateway:rate-limit:principal:alice:/limited")),
                eq("30000")))
                .thenReturn(3L);

        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(2);
        policy.setWindow(Duration.ofSeconds(30));

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

        assertThat(limiter.allow("principal:alice:/limited", policy)).isFalse();
    }
}
