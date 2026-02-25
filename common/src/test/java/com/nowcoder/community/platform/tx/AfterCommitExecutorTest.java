package com.nowcoder.community.platform.tx;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitExecutorTest {

    @Test
    void runAfterCommit_shouldRunImmediatelyWhenNoTransaction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        AfterCommitExecutor.runAfterCommit(() -> ran.set(true));
        assertThat(ran.get()).isTrue();
    }

    @Test
    void runAfterCommit_shouldRunOnlyAfterCommitWhenTransactionActive() {
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            AfterCommitExecutor.runAfterCommit(() -> ran.set(true));

            // 事务内不应执行（只注册 afterCommit 回调）
            assertThat(ran.get()).isFalse();

            // 模拟事务提交：触发所有 afterCommit 回调
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        assertThat(ran.get()).isTrue();
    }
}
