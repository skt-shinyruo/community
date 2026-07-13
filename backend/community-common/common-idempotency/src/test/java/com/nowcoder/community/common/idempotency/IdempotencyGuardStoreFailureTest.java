package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.SimpleErrorCode;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardStoreFailureTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    private static JsonCodec jsonCodec() {
        return new JacksonJsonCodec(JsonMappers.standard());
    }

    @Test
    void executeRequiredShouldExtendProcessingAndReturnConflictWhenSaveSuccessFails() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class))).thenReturn(true);
        doThrow(new RuntimeException("redis down"))
                .when(store).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));

        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setSuccessTtl(Duration.ofMinutes(10));

        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, properties);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, () -> {
            supplierCalls.incrementAndGet();
            return "OK";
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getErrorCode().getCode()).isEqualTo(409);
                    assertThat(error.getMessage()).isEqualTo("请求结果确认中，请稍后重试");
                });

        assertThat(supplierCalls).hasValue(1);
        verify(store).extendProcessing(eq("op"), eq(USER_ID), eq("k1"), eq(Duration.ofMinutes(10)));
        verify(store, never()).delete(anyString(), any(), anyString());
    }

    @Test
    void executeRequiredShouldRejectReplayWhenRequestHashDiffers() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-b"), any(Duration.class))).thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "k1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-a"));

        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());
        AtomicInteger supplierCalls = new AtomicInteger();

        assertThatThrownBy(() -> guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "k1",
                "hash-b",
                new SimpleErrorCode(17007, "请求号与已有钱包请求不一致", 409),
                String.class,
                () -> {
                    supplierCalls.incrementAndGet();
                    return "NEW";
                }))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException error = (BusinessException) ex;
                    assertThat(error.getErrorCode().getCode()).isEqualTo(17007);
                    assertThat(error.getErrorCode().getHttpStatus()).isEqualTo(409);
                });

        assertThat(supplierCalls).hasValue(0);
    }

    @Test
    void executeRequiredShouldReplaySuccessWhenRequestHashMatches() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-a"), any(Duration.class))).thenReturn(false);
        when(store.get("wallet:recharge", USER_ID, "k1"))
                .thenReturn(new IdempotencyStore.Entry(IdempotencyStore.Status.SUCCESS, "\"OK\"", "hash-a"));

        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());

        String result = guard.executeRequired(
                "wallet:recharge",
                USER_ID,
                "k1",
                "hash-a",
                new SimpleErrorCode(17007, "请求号与已有钱包请求不一致", 409),
                String.class,
                () -> "NEW"
        );

        assertThat(result).isEqualTo("OK");
    }

    @Test
    void executeRequiredShouldSaveSuccessOnlyAfterTransactionCommit() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class))).thenReturn(true);
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            String result = guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, () -> "OK");

            assertThat(result).isEqualTo("OK");
            verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        verify(store).saveSuccess(eq("op"), eq(USER_ID), eq("k1"), eq("hash-1"), eq("\"OK\""), any(Duration.class));
        verify(store, never()).delete(anyString(), any(), anyString());
    }

    @Test
    void executeRequiredShouldDeleteProcessingKeyWhenTransactionRollsBackAfterSupplierSucceeded() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class))).thenReturn(true);
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec(), store, null, new IdempotencyProperties());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            String result = guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, () -> "OK");

            assertThat(result).isEqualTo("OK");
            verify(store).tryAcquireProcessing(eq("op"), eq(USER_ID), eq("k1"), eq("hash-1"), any(Duration.class));
            verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
            verify(store, never()).delete(anyString(), any(), anyString());
        } finally {
            try {
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }

        verify(store).tryAcquireProcessing(eq("op"), eq(USER_ID), eq("k1"), eq("hash-1"), any(Duration.class));
        verify(store).delete(eq("op"), eq(USER_ID), eq("k1"));
        verify(store, never()).saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class));
    }
}
