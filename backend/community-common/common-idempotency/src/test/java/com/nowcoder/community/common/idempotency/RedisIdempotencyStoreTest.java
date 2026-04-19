package com.nowcoder.community.common.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void extendProcessingShouldOnlyRefreshProcessingState() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("idem:op:1:k1")), eq("45000")))
                .thenReturn(1L);

        store.extendProcessing("op", 1, "k1", Duration.ofSeconds(45));

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("idem:op:1:k1")), eq("45000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('get', KEYS[1]) == 'P'");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire'");
    }
}
