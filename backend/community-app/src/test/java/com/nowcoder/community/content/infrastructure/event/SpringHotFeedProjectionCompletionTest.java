package com.nowcoder.community.content.infrastructure.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SpringHotFeedProjectionCompletionTest {

    private final SpringHotFeedProjectionCompletion completion = new SpringHotFeedProjectionCompletion();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void withoutTransactionSynchronizationCommitsImmediately() {
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger rolledBack = new AtomicInteger();

        completion.afterTransaction(committed::incrementAndGet, rolledBack::incrementAndGet);

        assertThat(committed).hasValue(1);
        assertThat(rolledBack).hasValue(0);
    }

    @Test
    void activeTransactionSynchronizationCommitsOnlyAfterCommit() {
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger rolledBack = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        completion.afterTransaction(committed::incrementAndGet, rolledBack::incrementAndGet);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);

        assertThat(committed).hasValue(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(committed).hasValue(1);
        assertThat(rolledBack).hasValue(0);
    }

    @Test
    void rolledBackTransactionAbortsWithoutCommitting() {
        AtomicInteger committed = new AtomicInteger();
        AtomicInteger rolledBack = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        completion.afterTransaction(committed::incrementAndGet, rolledBack::incrementAndGet);
        TransactionSynchronization synchronization = TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(committed).hasValue(0);
        assertThat(rolledBack).hasValue(1);
    }
}
