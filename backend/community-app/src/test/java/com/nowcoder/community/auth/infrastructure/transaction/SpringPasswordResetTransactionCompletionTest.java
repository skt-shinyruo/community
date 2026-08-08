package com.nowcoder.community.auth.infrastructure.transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringPasswordResetTransactionCompletionTest {

    private final SpringPasswordResetTransactionCompletion completion =
            new SpringPasswordResetTransactionCompletion();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRunCompensationOnlyAfterRollback() {
        AtomicInteger executions = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        completion.afterRollback(executions::incrementAndGet);

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(executions).hasValue(0);

        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(executions).hasValue(1);
    }

    @Test
    void shouldIgnoreRegistrationWhenNoTransactionIsActive() {
        AtomicInteger executions = new AtomicInteger();

        completion.afterRollback(executions::incrementAndGet);

        assertThat(executions).hasValue(0);
    }
}
