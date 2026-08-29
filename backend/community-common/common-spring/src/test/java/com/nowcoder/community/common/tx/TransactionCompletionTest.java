package com.nowcoder.community.common.tx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionCompletionTest {

    private final TransactionCompletion completion = new TransactionCompletion();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackActionRunsOnlyAfterRollback() {
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
    void rollbackActionIsSkippedWhenNoTransactionIsActive() {
        AtomicInteger executions = new AtomicInteger();

        completion.afterRollback(executions::incrementAndGet);

        assertThat(executions).hasValue(0);
    }

    @Test
    void commitActionRunsOnlyAfterCommit() {
        AtomicInteger committed = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        completion.afterCommit(committed::incrementAndGet);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);

        assertThat(committed).hasValue(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(committed).hasValue(1);
    }

    @Test
    void commitActionRunsImmediatelyWhenNoSynchronizationIsActive() {
        AtomicInteger committed = new AtomicInteger();

        completion.afterCommit(committed::incrementAndGet);

        assertThat(committed).hasValue(1);
    }
}
