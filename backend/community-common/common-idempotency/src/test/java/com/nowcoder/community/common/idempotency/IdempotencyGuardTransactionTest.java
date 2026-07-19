package com.nowcoder.community.common.idempotency;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.json.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyGuardTransactionTest {

    private static final UUID USER_ID = UUID.fromString("01966f76-9d81-7f10-8d11-223344556677");

    @Test
    void executeRequiredShouldRejectNonTransactionalStoreBeforeStoreOrSupplierAccess() {
        IdempotencyStore store = mock(IdempotencyStore.class);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertStoreUnavailable(guard(store), supplierCalls);

        assertThat(supplierCalls).hasValue(0);
        verify(store, never()).tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredShouldRejectTransactionalStoreWithoutActiveTransaction() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(false);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        AtomicInteger supplierCalls = new AtomicInteger();

        assertStoreUnavailable(guard(store), supplierCalls);

        assertThat(supplierCalls).hasValue(0);
        verify(store, never()).tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredShouldRejectTransactionBoundToDifferentDataSource() {
        DataSource storeDataSource = dataSource("guard-store");
        DataSource transactionDataSource = dataSource("guard-other-transaction");
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        when(store.isEnlistedInCurrentTransaction())
                .thenAnswer(invocation -> TransactionSynchronizationManager.hasResource(storeDataSource));
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        AtomicInteger supplierCalls = new AtomicInteger();
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(transactionDataSource));

        transaction.executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.hasResource(transactionDataSource)).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(storeDataSource)).isFalse();
            assertStoreUnavailable(guard(store), supplierCalls);
        });

        assertThat(supplierCalls).hasValue(0);
        verify(store, never()).tryAcquireProcessing(anyString(), any(), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void executeRequiredShouldSaveSuccessBeforeReturningFromCurrentTransaction() {
        TransactionalIdempotencyStore store = mock(TransactionalIdempotencyStore.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        @SuppressWarnings("unchecked")
        Supplier<String> supplier = (Supplier<String>) mock(Supplier.class);
        when(store.isEnlistedInCurrentTransaction()).thenReturn(true);
        when(store.tryAcquireProcessing(anyString(), any(), anyString(), eq("hash-1"), any(Duration.class)))
                .thenReturn(true);
        when(store.saveSuccess(anyString(), any(), anyString(), anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(supplier.get()).thenReturn("OK");
        when(jsonCodec.toJson("OK")).thenReturn("\"OK\"");
        IdempotencyGuard guard = new IdempotencyGuard(jsonCodec, store, null, new IdempotencyProperties());

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            String result = guard.executeRequired("op", USER_ID, "k1", "hash-1", null, String.class, supplier);

            assertThat(result).isEqualTo("OK");
            var ordered = inOrder(supplier, jsonCodec, store);
            ordered.verify(supplier).get();
            ordered.verify(jsonCodec).toJson("OK");
            ordered.verify(store).saveSuccess(
                    eq("op"), eq(USER_ID), eq("k1"), eq("hash-1"), eq("\"OK\""), any(Duration.class));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void assertStoreUnavailable(IdempotencyGuard guard, AtomicInteger supplierCalls) {
        assertThatThrownBy(() -> guard.executeRequired(
                "op", USER_ID, "k1", "hash-1", null, String.class, () -> {
                    supplierCalls.incrementAndGet();
                    return "OK";
                }))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isSameAs(IdempotencyErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
                    assertThat(error.getErrorCode().getCode()).isEqualTo(503);
                });
    }

    private static IdempotencyGuard guard(IdempotencyStore store) {
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.toJson(any())).thenReturn("\"OK\"");
        return new IdempotencyGuard(jsonCodec, store, null, new IdempotencyProperties());
    }

    private static DataSource dataSource(String name) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        return dataSource;
    }
}
