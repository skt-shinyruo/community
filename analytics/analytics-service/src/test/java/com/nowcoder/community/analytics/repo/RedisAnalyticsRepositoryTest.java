package com.nowcoder.community.analytics.repo;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalyticsRepositoryTest {

    @Test
    void calculateUv_shouldUseTemporaryUnionKey_andCleanup() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hyperOps = (HyperLogLogOperations<String, String>) mock(HyperLogLogOperations.class);

        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperOps);
        when(hyperOps.union(anyString(), any(String[].class))).thenReturn(1L);
        when(hyperOps.size(anyString())).thenReturn(42L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        long uv = repo.calculateUv(start, end);
        assertThat(uv).isEqualTo(42L);

        ArgumentCaptor<String> deleteKey = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(1)).delete(deleteKey.capture());

        String key = deleteKey.getValue();
        assertThat(key).startsWith("uv:tmp:2026-01-01:2026-01-02:");
        assertThat(key).isNotEqualTo("uv:2026-01-01:2026-01-02");
        verify(redisTemplate, times(1)).expire(eq(key), any(Duration.class));
    }

    @Test
    void calculateDau_shouldUseTemporaryUnionKey_andCleanup_andBeUniquePerCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        AtomicInteger executeCalls = new AtomicInteger();
        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            int n = executeCalls.incrementAndGet();
            return (n % 2 == 1) ? null : 5L;
        });

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        long dau1 = repo.calculateDau(start, end);
        long dau2 = repo.calculateDau(start, end);
        assertThat(dau1).isEqualTo(5L);
        assertThat(dau2).isEqualTo(5L);

        ArgumentCaptor<String> deleteKey = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, times(2)).delete(deleteKey.capture());

        List<String> keys = deleteKey.getAllValues();
        assertThat(keys).hasSize(2);
        assertThat(keys.get(0)).startsWith("dau:tmp:2026-01-01:2026-01-02:");
        assertThat(keys.get(1)).startsWith("dau:tmp:2026-01-01:2026-01-02:");
        assertThat(keys.get(0)).isNotEqualTo(keys.get(1));
    }

    @Test
    void calculateDau_shouldCleanupEvenWhenBitCountFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn(null)
                .thenThrow(new RuntimeException("boom"));

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> repo.calculateDau(start, end)).isInstanceOf(RuntimeException.class);

        verify(redisTemplate, times(1)).delete(anyString());
    }
}

