package com.nowcoder.community.infra.tx;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 事务提交后执行工具。
 *
 * <p>用途：在同一请求线程内，将“非 DB 副作用”（例如 Kafka 发送、缓存刷新）推迟到事务 commit 之后，
 * 避免出现“DB 回滚但副作用已发生”的幽灵行为。</p>
 *
 * <p>注意：该工具不保证最终执行成功（例如 Kafka 发送失败）。P0 阶段仅用于消除幽灵事件；可靠投递由 P1 Outbox 方案补齐。</p>
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

        // 当前没有事务：直接执行（调用方自行保证幂等与可重试策略）
        action.run();
    }
}
