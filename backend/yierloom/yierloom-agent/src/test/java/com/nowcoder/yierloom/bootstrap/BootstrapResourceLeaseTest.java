package com.nowcoder.yierloom.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapResourceLeaseTest {

    @Test
    void successfulRetryUnregistersTheTemporaryShutdownHook() {
        AtomicInteger attempts = new AtomicInteger();
        BootstrapResourceLease lease = new BootstrapResourceLease(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("transient cleanup failure");
            }
        });

        assertThatThrownBy(lease::cleanupNow).isInstanceOf(IllegalStateException.class);
        assertThat(lease.retryHookRegistered()).isTrue();

        lease.cleanupNow();

        assertThat(attempts).hasValue(2);
        assertThat(lease.retryHookRegistered()).isFalse();
    }

    @Test
    void coreCleanupRequestIsDeferredUntilReflectiveLaunchHasReturned() {
        AtomicInteger cleanups = new AtomicInteger();
        BootstrapResourceLease lease = new BootstrapResourceLease(cleanups::incrementAndGet);

        lease.acceptOwnership();
        lease.run();

        assertThat(lease.cleanupRequested()).isTrue();
        assertThat(cleanups).hasValue(0);

        lease.completeLaunch();

        assertThat(cleanups).hasValue(1);
    }

    @Test
    void shutdownCleanupRunsOnlyAfterTheCoreCallbackReturns() {
        List<String> calls = new ArrayList<>();
        BootstrapResourceLease lease = new BootstrapResourceLease(() -> calls.add("cleanup"));
        lease.run();

        lease.runAtShutdown(() -> {
            calls.add("core");
            assertThat(calls).containsExactly("core");
        });

        assertThat(calls).containsExactly("core", "cleanup");
    }

    @Test
    void ownershipCanReturnToTheLauncherAfterCoreCleanupFails() {
        BootstrapResourceLease lease = new BootstrapResourceLease(() -> { });

        lease.acceptOwnership();
        assertThat(lease.ownershipAccepted()).isTrue();

        lease.releaseOwnership();
        assertThat(lease.ownershipAccepted()).isFalse();
    }
}
