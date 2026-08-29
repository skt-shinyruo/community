package com.nowcoder.community.common.tx;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Runs follow-up work when the current transaction completes. When no
 * transaction synchronization is active, rollback actions are skipped and
 * commit actions run immediately.
 */
@Component
public class TransactionCompletion {

    public void afterRollback(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }

    public void afterCommit(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
