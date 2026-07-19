package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.ErrorKind;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

    @Test
    void executeRequiredWithFingerprintShouldReturnCachedResponseWithoutCallingSupplier() {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-1"));
        IdempotencyGuard guard = guard(store);
        AtomicInteger supplierCalls = new AtomicInteger();

        String result = guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "hash-1",
                new SimpleErrorCode(17007, "replay conflict", ErrorKind.CONFLICT),
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
    void executeRequiredWithFingerprintShouldUseCallerConflictCodeWhenFingerprintDiffers() {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-new"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-old"));
        IdempotencyGuard guard = guard(store);
        SimpleErrorCode conflictCode = new SimpleErrorCode(17007, "replay conflict", ErrorKind.CONFLICT);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "hash-new",
                conflictCode,
                String.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return "NEW";
                }
        ))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isSameAs(conflictCode));

        assertThat(supplierCalls).hasValue(0);
        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredWithFingerprintShouldReturnStableProcessingError() {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("market:create_order", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.PROCESSING, null, "hash-1"));
        IdempotencyGuard guard = guard(store);

        assertThatThrownBy(() -> guard.executeRequired(
                "market:create_order",
                USER_ID,
                "idem-1",
                "hash-1",
                new SimpleErrorCode(18001, "replay conflict", ErrorKind.CONFLICT),
                String.class,
                () -> "NEW"
        ))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isSameAs(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS);
                    assertThat(error.getMessage()).isEqualTo(IdempotencyErrorCode.IDEMPOTENCY_IN_PROGRESS.getMessage());
                });
    }

    @Test
    void executeRequiredWithFingerprintShouldReturnStableIndeterminateError() {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("market:create_order", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.INDETERMINATE, null, "hash-1"));
        IdempotencyGuard guard = guard(store);

        assertThatThrownBy(() -> guard.executeRequired(
                "market:create_order",
                USER_ID,
                "idem-1",
                "hash-1",
                new SimpleErrorCode(18001, "replay conflict", ErrorKind.CONFLICT),
                String.class,
                () -> "NEW"
        ))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isSameAs(IdempotencyErrorCode.IDEMPOTENCY_OUTCOME_INDETERMINATE);
                    assertThat(error.getMessage()).isEqualTo("请求结果不确定，请查询业务状态");
                    assertThat(error.getMessage()).doesNotContain("key", "幂等键");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "{broken-json"})
    void executeRequiredShouldRejectMissingOrCorruptCachedResponse(String responseJson) {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), eq(USER_ID), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "idem-1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, responseJson, "hash-1"));
        IdempotencyGuard guard = guard(store);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "hash-1",
                null,
                String.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return "NEW";
                }
        ))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isSameAs(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
                    assertThat(error.getErrorCode().getCode()).isEqualTo(503);
                });

        assertThat(supplierCalls).hasValue(0);
    }

    @Test
    void executeRequiredShouldValidateFingerprintBeforeTransactionEligibility() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        IdempotencyGuard guard = guard(store);

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "idem-1",
                "h".repeat(65),
                new SimpleErrorCode(17007, "replay conflict", ErrorKind.CONFLICT),
                String.class,
                () -> "NEW"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requestHash 过长");

        verify(store, never()).isEnlistedInCurrentTransaction();
        verify(store, never()).tryAcquireProcessing(
                anyString(), any(), anyString(), anyString(), any(Duration.class));
    }

    private static TransactionalIdempotencyStore enlistedStore() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        return store;
    }

    private static IdempotencyGuard guard(IdempotencyStore store) {
        return new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
    }

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }
}
