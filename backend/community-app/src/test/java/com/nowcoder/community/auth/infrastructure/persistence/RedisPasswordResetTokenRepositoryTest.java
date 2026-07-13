package com.nowcoder.community.auth.infrastructure.persistence;

import com.nowcoder.community.auth.domain.repository.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPasswordResetTokenRepositoryTest {

    @Test
    void consumeWithTtlShouldAtomicallyReturnUserIdAndRemainingTtl() {
        UUID userId = uuid(42);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:pwdreset:reset-token"))))
                .thenReturn(userId + "|123000");

        RedisPasswordResetTokenRepository store = new RedisPasswordResetTokenRepository(redisTemplate);

        PasswordResetTokenRepository.ConsumedPasswordResetToken consumed = store.consumeWithTtl("reset-token");

        assertThat(consumed.userId()).isEqualTo(userId);
        assertThat(consumed.remainingTtl()).isEqualTo(Duration.ofSeconds(123));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("auth:pwdreset:reset-token")));
    }

    @Test
    void consumeShouldReturnUserIdFromAtomicConsumeResult() {
        UUID userId = uuid(43);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:pwdreset:reset-token"))))
                .thenReturn(userId + "|123000");
        RedisPasswordResetTokenRepository store = new RedisPasswordResetTokenRepository(redisTemplate);

        assertThat(store.consume("reset-token")).isEqualTo(userId);
    }

    @Test
    void deleteShouldTrimToken() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisPasswordResetTokenRepository store = new RedisPasswordResetTokenRepository(redisTemplate);

        store.delete(" reset-token ");

        verify(redisTemplate).delete("auth:pwdreset:reset-token");
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
