package com.nowcoder.community.common.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisIdempotencyStoreTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void extendProcessingShouldOnlyRefreshProcessingState() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("idem:op:" + USER_ID + ":k1")), eq("45000")))
                .thenReturn(1L);

        store.extendProcessing("op", USER_ID, "k1", Duration.ofSeconds(45));

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of("idem:op:" + USER_ID + ":k1")), eq("45000"));
        assertThat(scriptCaptor.getValue()).isInstanceOf(DefaultRedisScript.class);
        DefaultRedisScript<?> script = (DefaultRedisScript<?>) scriptCaptor.getValue();
        assertThat(script.getScriptAsString()).contains("redis.call('get', KEYS[1]) == 'P'");
        assertThat(script.getScriptAsString()).contains("redis.call('pexpire'");
    }

    @Test
    void tryAcquireProcessingShouldStoreRequestHashWhenProvided() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("idem:wallet:recharge:" + USER_ID + ":k1", "P\nhash-a", Duration.ofSeconds(30)))
                .thenReturn(Boolean.TRUE);

        boolean acquired = store.tryAcquireProcessing("wallet:recharge", USER_ID, "k1", "hash-a", Duration.ofSeconds(30));

        assertThat(acquired).isTrue();
        verify(valueOps).setIfAbsent("idem:wallet:recharge:" + USER_ID + ":k1", "P\nhash-a", Duration.ofSeconds(30));
    }

    @Test
    void getShouldDecodeRequestHashFromSuccessfulValue() {
        RedisIdempotencyStore store = new RedisIdempotencyStore(redisTemplate);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idem:wallet:recharge:" + USER_ID + ":k1")).thenReturn("S\nhash-a\n\"OK\"");

        IdempotencyStore.Entry entry = store.get("wallet:recharge", USER_ID, "k1");

        assertThat(entry.status()).isEqualTo(IdempotencyStore.Status.SUCCESS);
        assertThat(entry.requestHash()).isEqualTo("hash-a");
        assertThat(entry.successJson()).isEqualTo("\"OK\"");
    }
}
