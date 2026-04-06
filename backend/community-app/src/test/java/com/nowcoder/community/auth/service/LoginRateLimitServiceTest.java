package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginRateLimitServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MeterRegistry> meterRegistryProvider = mock(ObjectProvider.class);

    private LoginRateLimitService service;

    @BeforeEach
    void setUp() {
        LoginRateLimitProperties properties = new LoginRateLimitProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new LoginRateLimitService(properties, redisTemplate, meterRegistryProvider);
    }

    @Test
    void assertNotBlocked_shouldFailOpenQuicklyWhenRedisReadHangs() {
        when(valueOperations.get(anyString())).thenAnswer(invocation -> {
            Thread.sleep(250);
            return null;
        });

        assertTimeoutPreemptively(Duration.ofMillis(200),
                () -> service.assertNotBlocked("alice", "127.0.0.1", "remote"));
    }

    @Test
    void reset_shouldReturnQuicklyWhenRedisDeleteHangs() {
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> {
            Thread.sleep(250);
            return Boolean.TRUE;
        });

        assertTimeoutPreemptively(Duration.ofMillis(200),
                () -> service.reset("alice", "127.0.0.1"));
    }
}
