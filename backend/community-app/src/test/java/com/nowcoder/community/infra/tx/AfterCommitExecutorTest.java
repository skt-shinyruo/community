package com.nowcoder.community.common.tx;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceId;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void runAfterCommit_shouldCaptureTraceAtRegistrationAndRestorePreviousAfterCallback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            TraceContext.set("11111111111111111111111111111111");
            AtomicReference<String> seen = new AtomicReference<>();

            AfterCommitExecutor.runAfterCommit(() -> seen.set(TraceId.get()));

            TraceContext.set("22222222222222222222222222222222");
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            assertThat(seen.get()).isEqualTo("11111111111111111111111111111111");
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TraceContext.clear();
        }
    }

    @Test
    void runAfterCommit_shouldUseCapturedTraceWhenAnotherOtelSpanIsActiveAtCallback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
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
                AfterCommitExecutor.runAfterCommit(() -> seen.set(TraceId.get()));
            }

            try (Scope ignored = Span.wrap(callbackSpan).makeCurrent()) {
                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                }

                assertThat(seen.get()).isEqualTo("11111111111111111111111111111111");
                assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TraceContext.clear();
        }
    }
}
