package com.nowcoder.community.content.infrastructure.persistence;

import com.nowcoder.community.content.application.ContentHotPathProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisHotPathSingleFlightTest {

    @Test
    void executeShouldRunLoaderWhenLockIsAcquired() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sf:hot-path:feed:key-1"), any(String.class), eq(Duration.ofMillis(100))))
                .thenReturn(true);
        RedisHotPathSingleFlight singleFlight = new RedisHotPathSingleFlight(redisTemplate, new ContentHotPathProperties());

        String result = singleFlight.execute("feed", "key-1", Duration.ofMillis(100), () -> "loaded", () -> "busy");

        assertThat(result).isEqualTo("loaded");
        verify(redisTemplate).execute(any(), eq(Collections.singletonList("sf:hot-path:feed:key-1")), any(String.class));
    }

    @Test
    void executeShouldUseBusyFallbackWhenLockIsHeld() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("sf:hot-path:feed:key-1"), any(String.class), eq(Duration.ofMillis(100))))
                .thenReturn(false);
        RedisHotPathSingleFlight singleFlight = new RedisHotPathSingleFlight(redisTemplate, new ContentHotPathProperties());
        AtomicInteger loaderCalls = new AtomicInteger();

        String result = singleFlight.execute("feed", "key-1", Duration.ofMillis(100), () -> {
            loaderCalls.incrementAndGet();
            return "loaded";
        }, () -> "busy");

        assertThat(result).isEqualTo("busy");
        assertThat(loaderCalls).hasValue(0);
    }
}
