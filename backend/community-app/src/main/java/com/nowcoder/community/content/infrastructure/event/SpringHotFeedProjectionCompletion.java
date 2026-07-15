package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.application.HotFeedProjectionCompletion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

@Component
public class SpringHotFeedProjectionCompletion implements HotFeedProjectionCompletion {

    @Override
    public void afterTransaction(Runnable committedAction, Runnable rolledBackAction) {
        Objects.requireNonNull(committedAction, "committedAction must not be null");
        Objects.requireNonNull(rolledBackAction, "rolledBackAction must not be null");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            committedAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                committedAction.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    rolledBackAction.run();
                }
            }
        });
    }
}
