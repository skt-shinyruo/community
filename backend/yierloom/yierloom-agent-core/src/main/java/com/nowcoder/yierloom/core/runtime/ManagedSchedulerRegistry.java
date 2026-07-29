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
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;

public final class ManagedSchedulerRegistry implements AutoCloseable {
    private final ScheduledExecutorService executor;
    private final Map<String, PluginManagedScheduler> schedulers = new HashMap<>();
    private final Object lifecycleGate = new Object();
    private boolean acceptingTasks = true;
    private boolean closed;

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

    public void closePlugin(String pluginId) {
        synchronized (lifecycleGate) {
            PluginManagedScheduler scheduler = schedulers.remove(pluginId);
            if (scheduler != null) {
                scheduler.cancelAll();
            }
        }
    }

    public int taskCount(String pluginId) {
        synchronized (lifecycleGate) {
            PluginManagedScheduler scheduler = schedulers.get(pluginId);
            return scheduler == null ? 0 : scheduler.taskCount();
        }
    }

    @Override
    public void close() {
        List<PluginManagedScheduler> ownedSchedulers;
        synchronized (lifecycleGate) {
            if (closed) {
                return;
            }
            acceptingTasks = false;
            closed = true;
            ownedSchedulers = new ArrayList<>(schedulers.values());
            schedulers.clear();
            ownedSchedulers.forEach(PluginManagedScheduler::cancelAll);
        }
        executor.shutdownNow();
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

    private static ScheduledThreadPoolExecutor createExecutor() {
        int threadCount = Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                threadCount,
                daemonThreadFactory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
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
}
