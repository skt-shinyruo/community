package com.nowcoder.community.common.idempotency;

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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardStoreFailureTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    @Test
    void executeRequiredShouldFailWhenSaveSuccessDoesNotUpdateTheClaim() {
        TransactionalIdempotencyStore store = enlistedStore();
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);
        IdempotencyGuard guard = guard(store);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, () -> {
            supplierCalls.incrementAndGet();
            return "OK";
        }))
                .isInstanceOfAny(IllegalStateException.class, com.nowcoder.community.common.exception.BusinessException.class);

        assertThat(supplierCalls).hasValue(1);
        verify(store, never()).extendProcessing(anyString(), any(), anyString(), any(Duration.class));
        verify(store, never()).delete(anyString(), any(), anyString());
    }

    @Test
    void executeRequiredShouldPropagateSaveSuccessFailureWithoutCompensation() {
        TransactionalIdempotencyStore store = enlistedStore();
        RuntimeException failure = new RuntimeException("database write failed");
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        doThrow(failure)
                .when(store).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
        IdempotencyGuard guard = guard(store);

        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, String.class, () -> "OK"))
                .isSameAs(failure);

        verify(store, never()).extendProcessing(anyString(), any(), anyString(), any(Duration.class));
        verify(store, never()).delete(anyString(), any(), anyString());
    }

    @Test
    void executeRequiredShouldPropagateSupplierFailureWithoutCompensation() {
        TransactionalIdempotencyStore store = enlistedStore();
        RuntimeException failure = new RuntimeException("business write failed");
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        IdempotencyGuard guard = guard(store);

        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, String.class, () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        verify(store, never()).extendProcessing(anyString(), any(), anyString(), any(Duration.class));
        verify(store, never()).delete(anyString(), any(), anyString());
        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredShouldPropagateClaimStoreFailure() {
        TransactionalIdempotencyStore store = enlistedStore();
        RuntimeException failure = new RuntimeException("database unavailable");
        doThrow(failure)
                .when(store).tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class));
        IdempotencyGuard guard = guard(store);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, () -> {
            supplierCalls.incrementAndGet();
            return "OK";
        }))
                .isSameAs(failure);

        assertThat(supplierCalls).hasValue(0);
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
