package com.nowcoder.yierloom.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapFailuresTest {

    @Test
    void primaryFatalWinsOverAnOrdinaryCleanupFailure() {
        OutOfMemoryError fatal = new OutOfMemoryError("fatal");

        Throwable selected = BootstrapFailures.preferred(
                new IllegalStateException("wrapped", fatal),
                new SecurityException("cleanup"));

        assertThat(selected).isSameAs(fatal);
    }

    @Test
    void cleanupFatalWinsOverAnOrdinaryPrimaryFailure() {
        ThreadDeath fatal = new ThreadDeath();

        Throwable selected = BootstrapFailures.preferred(
                new IllegalStateException("primary"),
                new IllegalStateException("wrapped cleanup", fatal));

        assertThat(selected).isSameAs(fatal);
    }

    @Test
    void fatalTraversalIsUnboundedAndCycleSafe() {
        OutOfMemoryError fatal = new OutOfMemoryError("fatal");
        Throwable failure = fatal;
        for (int depth = 0; depth < 100; depth++) {
            failure = new IllegalStateException("wrapped", failure);
        }
        IllegalStateException cycle = new IllegalStateException("cycle");
        cycle.addSuppressed(cycleSafePeer(cycle));
        cycle.addSuppressed(failure);

        assertThat(BootstrapFailures.preferred(cycle, null)).isSameAs(fatal);
    }

    @Test
    void preservesBothOrdinaryOperationAndCleanupFailuresWithoutSuppression() {
        IllegalStateException primary = new IllegalStateException("primary");
        SecurityException cleanup = new SecurityException("cleanup");

        Throwable selected = BootstrapFailures.preferred(primary, cleanup);

        assertThat(selected).isInstanceOf(BootstrapFailures.CombinedFailure.class);
        assertThat(selected.getCause()).isSameAs(primary);
        assertThat(((BootstrapFailures.CombinedFailure) selected).cleanupFailure())
                .isSameAs(cleanup);
        assertThat(selected.getSuppressed()).isEmpty();
    }

    private static Throwable cycleSafePeer(Throwable root) {
        IllegalStateException peer = new IllegalStateException("peer");
        peer.addSuppressed(root);
        return peer;
    }
}
