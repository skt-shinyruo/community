package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardTtlTest {

    @Test
    void executeRequiredShouldUseConfiguredTtlWithTransactionalStore() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        when(store.tryAcquireProcessing(anyString(), any(UUID.class), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(UUID.class), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setProcessingTtl(Duration.ofSeconds(45));
        properties.setSuccessTtl(Duration.ofMinutes(10));
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, properties);
        UUID userId = uuid(1);

        guard.executeRequired("op", userId, "k1", "hash-1", null, String.class, () -> "OK");

        verify(store).tryAcquireProcessing("op", userId, "k1", "hash-1", Duration.ofSeconds(45));
        verify(store).saveSuccess("op", userId, "k1", "hash-1", "\"OK\"", Duration.ofMinutes(10));
    }

    @Test
    void executeRequiredShouldRejectRedisStoreBeforeSupplierOrRedisAccess() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        IdempotencyGuard guard = new IdempotencyGuard(
                jsonCodec(),
                new RedisIdempotencyStore(redisTemplate),
                null,
                new IdempotencyProperties()
        );
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired(
                "op", uuid(1), "k1", "hash-1", null, String.class, () -> {
                    supplierCalls.incrementAndGet();
                    return "OK";
                }))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isSameAs(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
                    assertThat(error.getErrorCode().getCode()).isEqualTo(503);
                });

        assertThat(supplierCalls).hasValue(0);
        verify(redisTemplate, never()).opsForValue();
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
