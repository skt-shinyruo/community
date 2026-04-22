package com.nowcoder.community.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPasswordResetTokenStoreTest {

    @Test
    void consumeShouldUseAtomicGetAndDelete() {
        UUID userId = uuid(42);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:pwdreset:reset-token")).thenReturn(userId.toString());

        RedisPasswordResetTokenStore store = new RedisPasswordResetTokenStore(redisTemplate);

        UUID consumed = store.consume("reset-token");

        assertThat(consumed).isEqualTo(userId);
        verify(valueOperations).getAndDelete("auth:pwdreset:reset-token");
        verify(redisTemplate, never()).delete("auth:pwdreset:reset-token");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
