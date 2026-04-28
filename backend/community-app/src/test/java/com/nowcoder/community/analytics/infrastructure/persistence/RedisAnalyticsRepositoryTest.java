package com.nowcoder.community.analytics.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAnalyticsRepositoryTest {

    @Test
    void recordUv_shouldAddIpToDailyHyperLogLogKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hyperOps = (HyperLogLogOperations<String, String>) mock(HyperLogLogOperations.class);
        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperOps);

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        repo.recordUv(LocalDate.of(2026, 1, 1), "1.1.1.1");

        verify(hyperOps).add("uv:2026-01-01", "1.1.1.1");
    }

    @Test
    void recordDau_shouldSetUserBitOnDailyBitmapKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        repo.recordDau(LocalDate.of(2026, 1, 1), 123);

        verify(valueOps).setBit("dau:2026-01-01", 123, true);
    }

    @Test
    void calculateUv_shouldUseTemporaryUnionKey_andCleanup() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HyperLogLogOperations<String, String> hyperOps = (HyperLogLogOperations<String, String>) mock(HyperLogLogOperations.class);

        when(redisTemplate.opsForHyperLogLog()).thenReturn(hyperOps);
        when(hyperOps.size(anyString())).thenReturn(42L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
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

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(1)).execute(
                scriptCaptor.capture(),
                keysCaptor.capture(),
                eq(Long.toString(Duration.ofSeconds(60).toMillis()))
        );
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("pfmerge");
        assertThat(script.getScriptAsString()).contains("pexpire");
        assertThat(keysCaptor.getValue()).containsExactly(
                key,
                "uv:2026-01-01",
                "uv:2026-01-02"
        );
        verify(redisTemplate, never()).expire(eq(key), any(Duration.class));
    }

    @Test
    void calculateDau_shouldUseTemporaryUnionKey_andCleanup_andBeUniquePerCall() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(5L);

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
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("boom"));

        RedisAnalyticsRepository repo = new RedisAnalyticsRepository(redisTemplate);

        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 2);

        assertThatThrownBy(() -> repo.calculateDau(start, end)).isInstanceOf(RuntimeException.class);

        verify(redisTemplate, times(1)).delete(anyString());
    }
}
