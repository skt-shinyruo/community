package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPasswordResetTokenStoreTest {

    @Test
    void consumeShouldUseAtomicGetAndDelete() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:pwdreset:reset-token")).thenReturn("42");

        RedisPasswordResetTokenStore store = new RedisPasswordResetTokenStore(redisTemplate);

        Integer userId = store.consume("reset-token");

        assertThat(userId).isEqualTo(42);
        verify(valueOperations).getAndDelete("auth:pwdreset:reset-token");
        verify(redisTemplate, never()).delete("auth:pwdreset:reset-token");
    }
}
