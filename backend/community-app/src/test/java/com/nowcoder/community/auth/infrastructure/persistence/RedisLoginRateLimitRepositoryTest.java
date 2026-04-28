package com.nowcoder.community.auth.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisLoginRateLimitRepositoryTest {

    @Test
    void incrementShouldUseAtomicIncrementScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60")))
                .thenReturn(1L);
        RedisLoginRateLimitRepository repository = new RedisLoginRateLimitRepository(redisTemplate);

        int count = repository.increment("auth:login:fail:ip:127.0.0.1", 60);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("auth:login:fail:ip:127.0.0.1")), eq("60"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('incr'");
        assertThat(script.getScriptAsString()).contains("redis.call('expire'");
    }
}
