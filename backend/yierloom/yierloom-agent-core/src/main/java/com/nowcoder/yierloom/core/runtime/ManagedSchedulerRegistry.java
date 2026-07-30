package com.nowcoder.yierloom.core.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.core.FatalFailures;

public final class ManagedSchedulerRegistry implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);
    private final ScheduledExecutorService executor;
    private final Map<String, PluginManagedScheduler> schedulers = new HashMap<>();
    private final Object lifecycleGate = new Object();
    private boolean acceptingTasks = true;
    private boolean closed;
    private volatile boolean quiesced;

    public ManagedSchedulerRegistry() {
        this(createExecutor());
    }

    ManagedSchedulerRegistry(ScheduledExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public ManagedScheduler forPlugin(String pluginId, EventSink events) {
        String ownerId = requirePluginId(pluginId);
        Objects.requireNonNull(events, "events");
        synchronized (lifecycleGate) {
            if (closed) {
                throw new IllegalStateException("scheduler registry is closed");
            }
            return schedulers.computeIfAbsent(
                    ownerId,
                    ignored -> new PluginManagedScheduler(ownerId, events, this));
        }
    }

    public void rejectNewTasks() {
        synchronized (lifecycleGate) {
            acceptingTasks = false;
        }
    }

    public boolean closePlugin(String pluginId) {
        PluginManagedScheduler scheduler;
        synchronized (lifecycleGate) {
            scheduler = schedulers.get(pluginId);
            if (scheduler != null) {
                scheduler.rejectNewTasks();
            }
        }
        if (scheduler == null) {
            return true;
        }
        boolean quiesced = scheduler.cancelAllAndAwait(deadlineAfter(SHUTDOWN_TIMEOUT));
        if (quiesced) {
            synchronized (lifecycleGate) {
                schedulers.remove(pluginId, scheduler);
            }
        }
        return quiesced;
    }

    public int taskCount(String pluginId) {
        synchronized (lifecycleGate) {
            PluginManagedScheduler scheduler = schedulers.get(pluginId);
            return scheduler == null ? 0 : scheduler.taskCount();
        }
    }

    @Override
    public void close() {
        closeAndAwait(SHUTDOWN_TIMEOUT);
    }

    public boolean closeAndAwait(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long deadline = deadlineAfter(timeout.compareTo(SHUTDOWN_TIMEOUT) > 0
                ? SHUTDOWN_TIMEOUT
                : timeout);
        List<PluginManagedScheduler> ownedSchedulers;
        synchronized (lifecycleGate) {
            if (closed) {
                return quiesced || awaitExecutor(deadline);
            }
            acceptingTasks = false;
            closed = true;
            ownedSchedulers = new ArrayList<>(schedulers.values());
            ownedSchedulers.forEach(PluginManagedScheduler::rejectNewTasks);
            schedulers.clear();
        }
        boolean tasksStopped = true;
        for (PluginManagedScheduler scheduler : ownedSchedulers) {
            if (!scheduler.cancelAllAndAwait(deadline)) {
                tasksStopped = false;
            }
        }
        executor.shutdownNow();
        quiesced = tasksStopped && awaitExecutor(deadline);
        return quiesced;
    }

    ManagedScheduledTask schedule(
            PluginManagedScheduler scheduler,
            String taskName,
            Duration initialDelay,
            Duration delay,
            Runnable task
    ) {
        synchronized (lifecycleGate) {
            if (!acceptingTasks || closed || schedulers.get(scheduler.pluginId()) != scheduler) {
                throw new IllegalStateException("scheduler is not accepting new tasks");
            }
            return scheduler.schedule(executor, taskName, initialDelay, delay, task);
        }
    }

    static ScheduledThreadPoolExecutor createExecutor() {
        int threadCount = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ScheduledThreadPoolExecutor executor = new FatalAwareScheduledExecutor(
                threadCount,
                daemonThreadFactory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private boolean awaitExecutor(long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            return executor.isTerminated();
        }
        try {
            return executor.awaitTermination(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long deadlineAfter(Duration timeout) {
        long now = System.nanoTime();
        long nanos;
        try {
            nanos = timeout.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
        if (nanos >= Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + nanos;
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "yierloom-scheduler-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String requirePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        return pluginId;
    }

    private static final class FatalAwareScheduledExecutor extends ScheduledThreadPoolExecutor {
        private FatalAwareScheduledExecutor(int corePoolSize, ThreadFactory threadFactory) {
            super(corePoolSize, threadFactory);
        }

        @Override
        protected void afterExecute(Runnable task, Throwable failure) {
            super.afterExecute(task, failure);
            Throwable candidate = failure;
            if (candidate == null && task instanceof Future<?> future && future.isDone()) {
                try {
                    future.get();
                } catch (CancellationException ignored) {
                    return;
                } catch (ExecutionException executionFailure) {
                    candidate = executionFailure;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            FatalFailures.rethrow(candidate);
        }
    }
}
