package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardFingerprintTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    @Test
    void executeRequiredWithFingerprintShouldReturnCachedResponseWhenFingerprintMatches() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-1"));
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        AtomicInteger supplierCalls = new AtomicInteger();

        String result = guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "hash-1",
                new SimpleErrorCode(17007, "replay conflict", 409),
                String.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return "NEW";
                }
        );

        assertThat(result).isEqualTo("OK");
        assertThat(supplierCalls).hasValue(0);
    }

    @Test
    void executeRequiredWithFingerprintShouldRejectReplayWhenFingerprintDiffers() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-new"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-old"));
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "hash-new",
                new SimpleErrorCode(17007, "replay conflict", 409),
                String.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return "NEW";
                }
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getErrorCode().getCode()).isEqualTo(17007);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(409);
                });
        assertThat(supplierCalls).hasValue(0);
        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredWithFingerprintShouldReturnProcessingConflictWhenSameFingerprintIsProcessing() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("market:create_order", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.PROCESSING, null, "hash-1"));
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());

        assertThatThrownBy(() -> guard.executeRequired(
                "market:create_order",
                USER_ID,
                "idem-1",
                "hash-1",
                new SimpleErrorCode(18001, "replay conflict", 409),
                String.class,
                () -> "NEW"
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getCode()).isEqualTo(409));
    }

    @Test
    void executeRequiredShouldRejectFingerprintLongerThanPersistenceContract() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "h".repeat(65),
                new SimpleErrorCode(17007, "replay conflict", 409),
                String.class,
                () -> "NEW"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requestHash 过长");

        verify(store, never()).tryAcquireProcessing(
                anyString(),
                any(),
                anyString(),
                anyString(),
                any(Duration.class)
        );
    }
}
