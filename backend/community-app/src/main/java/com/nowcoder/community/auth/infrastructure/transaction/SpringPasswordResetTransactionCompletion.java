package com.nowcoder.community.auth.infrastructure.transaction;

import com.nowcoder.community.auth.application.port.PasswordResetTransactionCompletion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Component
public class SpringPasswordResetTransactionCompletion implements PasswordResetTransactionCompletion {

    @Override
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
}
