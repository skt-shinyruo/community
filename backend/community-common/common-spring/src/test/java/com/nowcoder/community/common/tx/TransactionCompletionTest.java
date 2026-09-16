package com.nowcoder.community.common.tx;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void commitActionShouldCaptureTraceAtRegistrationAndRestorePreviousAfterCallback() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            TraceContext.set("11111111111111111111111111111111");
            AtomicReference<String> seen = new AtomicReference<>();

            completion.afterCommit(() -> seen.set(TraceId.get()));

            TraceContext.set("22222222222222222222222222222222");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            assertThat(seen.get()).isEqualTo("11111111111111111111111111111111");
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void commitActionShouldUseCapturedTraceWhenAnotherOtelSpanIsActiveAtCallback() {
        TransactionSynchronizationManager.initSynchronization();
        SpanContext registrationSpan = SpanContext.create(
                "11111111111111111111111111111111",
                "aaaaaaaaaaaaaaaa",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        SpanContext callbackSpan = SpanContext.create(
                "22222222222222222222222222222222",
                "bbbbbbbbbbbbbbbb",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        try {
            AtomicReference<String> seen = new AtomicReference<>();

            try (Scope ignored = Span.wrap(registrationSpan).makeCurrent()) {
                completion.afterCommit(() -> seen.set(TraceId.get()));
            }

            try (Scope ignored = Span.wrap(callbackSpan).makeCurrent()) {
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                }

                assertThat(seen.get()).isEqualTo("11111111111111111111111111111111");
                assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            }
        } finally {
            TraceContext.clear();
        }
    }
}
