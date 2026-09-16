package com.nowcoder.community.common.tx;

import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextScope;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import io.opentelemetry.api.trace.SpanKind;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Runs follow-up work when the current transaction completes. When no
 * transaction synchronization is active, rollback actions are skipped and
 * commit actions run immediately.
 *
 * <p>Commit actions capture the registering thread's trace context and run
 * inside a {@code tx.after_commit} span, so deferred side effects stay
 * attached to the trace that scheduled them.</p>
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

        TraceContextSnapshot captured = TraceContextSnapshot.currentOrNew();
        Runnable tracedAction = () -> {
            try (TraceContextScope ignored = OtelTraceContext.openForInbound(
                    captured.traceparent(),
                    "tx.after_commit",
                    SpanKind.INTERNAL
            )) {
                action.run();
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tracedAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tracedAction.run();
            }
        });
    }
}
