package com.nowcoder.yierloom.testkit.internal;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.testkit.PluginViolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractRuntimeHarnessTest {
    private static final String PLUGIN_ID = "runtime-harness-test";
    private static final PluginObservation LATE_OBSERVATION =
            PluginObservation.builder("late").build();

    @Test
    void pluginCodeDoesNotHoldPublicBridgeMonitorOrReceiveEndpointIdentity() {
        AtomicBoolean publicMonitorHeld = new AtomicBoolean();
        AtomicBoolean endpointExposed = new AtomicBoolean();
        PollutionEndpoint pollution = new PollutionEndpoint();
        RuntimeCapability runtime = new RuntimeCapability() {
            @Override
            public void start(PluginRuntimeContext context) {
                recordPublicMonitor(publicMonitorHeld);
                if (context.observations() instanceof YierLoomBridge.Endpoint endpoint) {
                    endpointExposed.set(true);
                    if (YierLoomBridge.clear(endpoint)) {
                        YierLoomBridge.install(pollution);
                    }
                }
                context.observations().register(observation ->
                        recordPublicMonitor(publicMonitorHeld));
            }

            @Override
            public void stop() {
                recordPublicMonitor(publicMonitorHeld);
            }
        };

        try {
            List<PluginViolation> violations = ContractRuntimeHarness.verify(
                    PLUGIN_ID, runtime, PluginConfig.empty());

            assertThat(violations).isEmpty();
            assertThat(publicMonitorHeld).isFalse();
            assertThat(endpointExposed).isFalse();
            assertThat(YierLoomBridge.emit(
                    PollutionEndpoint.ID,
                    DiagnosticEvent.builder("pollution-probe").build())).isFalse();
        } finally {
            YierLoomBridge.clear(pollution);
        }
        assertBridgeAvailable();
    }

    @Test
    void closeRejectsAdmissionAndWaitsForConcurrentFatalHandlerToBecomeSilent()
            throws Exception {
        TestVmError fatal = new TestVmError();
        BlockingFatalRuntime runtime = new BlockingFatalRuntime(fatal);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<List<PluginViolation>> verification = executor.submit(() ->
                ContractRuntimeHarness.verify(PLUGIN_ID, runtime, PluginConfig.empty()));

        try {
            assertThat(runtime.handlerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(runtime.stopsCompleted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(awaitObservationRejection()).isTrue();
            assertThat(verification).isNotDone();

            runtime.releaseHandler.countDown();

            assertThatThrownBy(() -> verification.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(ExecutionException.class, failure ->
                            assertThat(failure.getCause()).isSameAs(fatal));
            assertThat(runtime.handlerExited.getCount()).isZero();
            runtime.joinWorker();
            assertThat(runtime.workerReturned.getCount()).isZero();
        } finally {
            runtime.releaseHandler.countDown();
            awaitCompletion(verification);
            executor.shutdownNow();
            runtime.joinWorker();
        }
        assertBridgeAvailable();
    }

    @Test
    void maliciousCauseAccessorOnFirstStopStillAllowsCleanupAndFatalDiscovery() {
        TestVmError fatal = new TestVmError();
        CyclicAccessorFailure accessorFailure = new CyclicAccessorFailure();
        ThrowingCauseFailure stopFailure = new ThrowingCauseFailure(accessorFailure);
        accessorFailure.cause = stopFailure;
        accessorFailure.addSuppressed(fatal);
        FirstStopFatalRuntime runtime = new FirstStopFatalRuntime(stopFailure);

        assertThatThrownBy(() -> ContractRuntimeHarness.verify(
                PLUGIN_ID, runtime, PluginConfig.empty()))
                .isSameAs(fatal);

        assertThat(runtime.stopCalls).hasValue(2);
        assertThat(runtime.task.isCancelled()).isTrue();
        assertThatThrownBy(() -> runtime.context.observations().register(observation -> { }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runtime.context.scheduler().scheduleWithFixedDelay(
                "late", Duration.ZERO, Duration.ofSeconds(1), () -> { }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(runtime.context.events().emit(
                DiagnosticEvent.builder("late").build())).isFalse();
        assertBridgeAvailable();
    }

    private static void recordPublicMonitor(AtomicBoolean monitorHeld) {
        if (Thread.holdsLock(YierLoomBridge.class)) {
            monitorHeld.set(true);
        }
    }

    private static boolean awaitObservationRejection() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!YierLoomBridge.observe(PLUGIN_ID, LATE_OBSERVATION)) {
                return true;
            }
            Thread.sleep(1);
        }
        return false;
    }

    private static void awaitCompletion(Future<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception | LinkageError ignored) {
            // The assertions above own the result; this path only drains cleanup.
        }
    }

    private static void assertBridgeAvailable() {
        PollutionEndpoint sentinel = new PollutionEndpoint();
        assertThat(YierLoomBridge.install(sentinel)).isTrue();
        assertThat(YierLoomBridge.clear(sentinel)).isTrue();
    }

    private static final class BlockingFatalRuntime implements RuntimeCapability {
        private final TestVmError fatal;
        private final CountDownLatch handlerEntered = new CountDownLatch(1);
        private final CountDownLatch handlerExited = new CountDownLatch(1);
        private final CountDownLatch releaseHandler = new CountDownLatch(1);
        private final CountDownLatch stopsCompleted = new CountDownLatch(1);
        private final CountDownLatch workerReturned = new CountDownLatch(1);
        private final AtomicInteger stopCalls = new AtomicInteger();
        private volatile Thread worker;

        private BlockingFatalRuntime(TestVmError fatal) {
            this.fatal = fatal;
        }

        @Override
        public void start(PluginRuntimeContext context) throws Exception {
            context.observations().register(observation -> {
                if (!"background".equals(observation.type())) {
                    return;
                }
                handlerEntered.countDown();
                try {
                    releaseHandler.await();
                    throw fatal;
                } finally {
                    handlerExited.countDown();
                }
            });
            worker = new Thread(() -> {
                try {
                    YierLoomBridge.observe(
                            PLUGIN_ID,
                            PluginObservation.builder("background").build());
                } finally {
                    workerReturned.countDown();
                }
            }, "yierloom-testkit-observation");
            worker.start();
            if (!handlerEntered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("observation handler did not start");
            }
        }

        @Override
        public void stop() {
            if (stopCalls.incrementAndGet() == 2) {
                stopsCompleted.countDown();
            }
        }

        private void joinWorker() throws InterruptedException {
            Thread current = worker;
            if (current != null) {
                current.join(TimeUnit.SECONDS.toMillis(5));
                assertThat(current.isAlive()).isFalse();
            }
        }
    }

    private static final class FirstStopFatalRuntime implements RuntimeCapability {
        private final RuntimeException stopFailure;
        private final AtomicInteger stopCalls = new AtomicInteger();
        private PluginRuntimeContext context;
        private ManagedTask task;

        private FirstStopFatalRuntime(RuntimeException stopFailure) {
            this.stopFailure = stopFailure;
        }

        @Override
        public void start(PluginRuntimeContext context) {
            this.context = context;
            context.observations().register(observation -> { });
            task = context.scheduler().scheduleWithFixedDelay(
                    "cleanup", Duration.ZERO, Duration.ofSeconds(1), () -> { });
        }

        @Override
        public void stop() {
            if (stopCalls.incrementAndGet() == 1) {
                throw stopFailure;
            }
        }
    }

    private static final class PollutionEndpoint implements YierLoomBridge.Endpoint {
        private static final String ID = "pollution";

        @Override
        public boolean observe(String pluginId, PluginObservation observation) {
            return ID.equals(pluginId);
        }

        @Override
        public boolean emit(String pluginId, DiagnosticEvent event) {
            return ID.equals(pluginId);
        }
    }

    private static final class ThrowingCauseFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final RuntimeException accessorFailure;

        private ThrowingCauseFailure(RuntimeException accessorFailure) {
            this.accessorFailure = accessorFailure;
        }

        @Override
        public synchronized Throwable getCause() {
            throw accessorFailure;
        }
    }

    private static final class CyclicAccessorFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private Throwable cause;

        @Override
        public synchronized Throwable getCause() {
            return cause;
        }
    }

    private static final class TestVmError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }
}
