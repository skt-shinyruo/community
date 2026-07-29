package com.nowcoder.yierloom.core.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ManagedTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedSchedulerRegistryTest {

    @Test
    void cancelsAllTasksOwnedByOnePluginWithoutTouchingAnother() {
        ManualScheduledExecutorService executor = new ManualScheduledExecutorService();
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(executor);
        ManagedTask alpha = registry.forPlugin("alpha", recordingSink()).scheduleWithFixedDelay(
                "summary", Duration.ZERO, Duration.ofSeconds(1), () -> { });
        ManagedTask beta = registry.forPlugin("beta", recordingSink()).scheduleWithFixedDelay(
                "summary", Duration.ZERO, Duration.ofSeconds(1), () -> { });

        registry.closePlugin("alpha");

        assertThat(alpha.isCancelled()).isTrue();
        assertThat(beta.isCancelled()).isFalse();
        assertThat(registry.taskCount("alpha")).isZero();
        assertThat(registry.taskCount("beta")).isEqualTo(1);
        registry.close();
    }

    @Test
    void thirdConsecutiveFailureCancelsTaskAndEmitsStatus() {
        ManualScheduledExecutorService executor = new ManualScheduledExecutorService();
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(executor);
        RecordingEventSink events = recordingSink();
        ManagedTask task = registry.forPlugin("alpha", events).scheduleWithFixedDelay(
                "summary", Duration.ZERO, Duration.ofSeconds(1), () -> {
                    throw new IllegalStateException("boom");
                });

        IntStream.range(0, 3).forEach(index -> executor.runTask(0));

        assertThat(task.isCancelled()).isTrue();
        assertThat(events.actions()).containsExactly("agent_task_disabled");
        assertThat(events.events().get(0).attributes())
                .containsEntry("task.name", "summary")
                .containsEntry("event.outcome", "failure");
        registry.close();
    }

    @Test
    void rejectingNewTasksDoesNotCancelExistingTasksUntilClose() {
        ManualScheduledExecutorService executor = new ManualScheduledExecutorService();
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(executor);
        ManagedScheduler scheduler = registry.forPlugin("alpha", recordingSink());
        ManagedTask existing = scheduler.scheduleWithFixedDelay(
                "existing", Duration.ZERO, Duration.ofSeconds(1), () -> { });

        registry.rejectNewTasks();

        assertThat(existing.isCancelled()).isFalse();
        assertThatThrownBy(() -> scheduler.scheduleWithFixedDelay(
                "new", Duration.ZERO, Duration.ofSeconds(1), () -> { }))
                .isInstanceOf(IllegalStateException.class);
        registry.close();
        assertThat(existing.isCancelled()).isTrue();
    }

    @Test
    void oneSuccessResetsTheConsecutiveFailureBudget() {
        ManualScheduledExecutorService executor = new ManualScheduledExecutorService();
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(executor);
        AtomicInteger calls = new AtomicInteger();
        ManagedTask task = registry.forPlugin("alpha", recordingSink()).scheduleWithFixedDelay(
                "summary", Duration.ZERO, Duration.ofSeconds(1), () -> {
                    int call = calls.incrementAndGet();
                    if (call != 3) {
                        throw new IllegalStateException("boom");
                    }
                });

        IntStream.range(0, 5).forEach(index -> executor.runTask(0));

        assertThat(task.isCancelled()).isFalse();
        registry.close();
    }

    @Test
    void taskNamesMustBeUniqueWithinOnePlugin() {
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(new ManualScheduledExecutorService());
        ManagedScheduler scheduler = registry.forPlugin("alpha", recordingSink());
        scheduler.scheduleWithFixedDelay("summary", Duration.ZERO, Duration.ofSeconds(1), () -> { });

        assertThatThrownBy(() -> scheduler.scheduleWithFixedDelay(
                "summary", Duration.ZERO, Duration.ofSeconds(1), () -> { }))
                .isInstanceOf(IllegalStateException.class);
        registry.close();
    }

    @Test
    void virtualMachineErrorsEscapeTheManagedTaskBoundary() {
        ManualScheduledExecutorService executor = new ManualScheduledExecutorService();
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry(executor);
        registry.forPlugin("alpha", recordingSink()).scheduleWithFixedDelay(
                "fatal", Duration.ZERO, Duration.ofSeconds(1), () -> {
                    throw new TestVirtualMachineError();
                });

        assertThatThrownBy(() -> executor.runTask(0))
                .isInstanceOf(TestVirtualMachineError.class);
        registry.close();
    }

    @Test
    void defaultRegistryRunsTasksOnNamedDaemonThreads() throws Exception {
        ManagedSchedulerRegistry registry = new ManagedSchedulerRegistry();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicBoolean daemon = new AtomicBoolean();
        AtomicReference<String> threadName = new AtomicReference<>();
        try {
            registry.forPlugin("alpha", recordingSink()).scheduleWithFixedDelay(
                    "probe", Duration.ZERO, Duration.ofDays(1), () -> {
                        Thread thread = Thread.currentThread();
                        daemon.set(thread.isDaemon());
                        threadName.set(thread.getName());
                        ran.countDown();
                    });

            assertThat(ran.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(daemon).isTrue();
            assertThat(threadName.get()).startsWith("yierloom-scheduler-");
        } finally {
            registry.close();
        }
    }

    private static RecordingEventSink recordingSink() {
        return new RecordingEventSink();
    }

    private static final class RecordingEventSink implements EventSink {
        private final List<DiagnosticEvent> events = new ArrayList<>();

        @Override
        public boolean emit(DiagnosticEvent event) {
            events.add(event);
            return true;
        }

        private List<DiagnosticEvent> events() {
            return List.copyOf(events);
        }

        private List<String> actions() {
            return events.stream().map(DiagnosticEvent::action).toList();
        }
    }

    private static final class TestVirtualMachineError extends VirtualMachineError {
        private static final long serialVersionUID = 1L;
    }

    private static final class ManualScheduledExecutorService extends AbstractExecutorService
            implements ScheduledExecutorService {
        private final List<ManualScheduledFuture> tasks = new ArrayList<>();
        private boolean shutdown;

        private synchronized void runTask(int index) {
            tasks.get(index).run();
        }

        @Override
        public synchronized ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit
        ) {
            if (shutdown) {
                throw new IllegalStateException("executor is shut down");
            }
            ManualScheduledFuture future = new ManualScheduledFuture(command);
            tasks.add(future);
            return future;
        }

        @Override
        public synchronized void shutdown() {
            shutdown = true;
        }

        @Override
        public synchronized List<Runnable> shutdownNow() {
            shutdown = true;
            tasks.forEach(task -> task.cancel(false));
            return List.of();
        }

        @Override
        public synchronized boolean isShutdown() {
            return shutdown;
        }

        @Override
        public synchronized boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command,
                long initialDelay,
                long period,
                TimeUnit unit
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ManualScheduledFuture implements ScheduledFuture<Object> {
        private final Runnable command;
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private ManualScheduledFuture(Runnable command) {
            this.command = command;
        }

        private void run() {
            if (!cancelled.get()) {
                command.run();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return cancelled.compareAndSet(false, true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return cancelled.get();
        }

        @Override
        public Object get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }
    }
}
