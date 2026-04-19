package com.nowcoder.community.auth.service;

import com.nowcoder.community.auth.config.LoginRateLimitProperties;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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
    void assertNotBlockedShouldFailClosedWhenRedisReadThrows() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.assertNotBlocked("alice", "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }

    @Test
    void isCaptchaRequiredShouldReturnTrueWhenRedisReadThrows() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(service.isCaptchaRequired("alice", "127.0.0.1")).isTrue();
    }

    @Test
    void recordFailureShouldUseAtomicIncrementScript() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60")))
                .thenReturn(1L);

        service.recordFailure(null, "127.0.0.1", "remote");

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('expire'");
    }

    @Test
    void recordFailureShouldFailClosedWhenRedisIncrementThrows() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60")))
                .thenThrow(new RuntimeException("redis down"));

        assertThatThrownBy(() -> service.recordFailure(null, "127.0.0.1", "remote"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
    }
}
