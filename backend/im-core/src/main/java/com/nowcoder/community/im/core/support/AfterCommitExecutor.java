package com.nowcoder.community.im.core.support;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Run side-effects only after transaction commit.
 *
 * <p>Used to avoid emitting Kafka events when the DB transaction rolls back.</p>
 */
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    public static void runAfterCommit(Runnable action) {
        if (action == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}

