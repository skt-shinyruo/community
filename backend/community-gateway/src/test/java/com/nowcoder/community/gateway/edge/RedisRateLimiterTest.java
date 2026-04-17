package com.nowcoder.community.gateway.edge;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTest {

    @Test
    void shouldExpireTheWindowWhenFirstIncrementSucceeds() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("gateway:rate-limit:principal:alice:/limited")).thenReturn(1L);

        RateLimitProperties.Policy policy = new RateLimitProperties.Policy();
        policy.setLimit(2);
        policy.setWindow(Duration.ofSeconds(30));

        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate);

        assertThat(limiter.allow("principal:alice:/limited", policy)).isTrue();
        verify(redisTemplate).expire("gateway:rate-limit:principal:alice:/limited", Duration.ofSeconds(30));
    }
}
