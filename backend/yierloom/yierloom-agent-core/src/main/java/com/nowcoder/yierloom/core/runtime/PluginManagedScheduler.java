package com.nowcoder.yierloom.core.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ManagedTask;

final class PluginManagedScheduler implements ManagedScheduler {
    private final String pluginId;
    private final EventSink events;
    private final ManagedSchedulerRegistry registry;
    private final ConcurrentMap<String, ManagedScheduledTask> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingTasks = new AtomicBoolean(true);

    PluginManagedScheduler(String pluginId, EventSink events, ManagedSchedulerRegistry registry) {
        this.pluginId = pluginId;
        this.events = events;
        this.registry = registry;
    }

    @Override
    public ManagedTask scheduleWithFixedDelay(
            String taskName,
            Duration initialDelay,
            Duration delay,
            Runnable task
    ) {
        return registry.schedule(this, taskName, initialDelay, delay, task);
    }

    String pluginId() {
        return pluginId;
    }

    ManagedScheduledTask schedule(
            ScheduledExecutorService executor,
            String taskName,
            Duration initialDelay,
            Duration delay,
            Runnable task
    ) {
        String name = requireTaskName(taskName);
        Duration startingDelay = requireNonNegative(initialDelay, "initialDelay");
        Duration recurringDelay = requirePositive(delay, "delay");
        Objects.requireNonNull(task, "task");
        if (!acceptingTasks.get()) {
            throw new IllegalStateException("plugin scheduler is closed");
        }

        ManagedScheduledTask managedTask = new ManagedScheduledTask(
                name,
                events,
                task,
                cancelled -> tasks.remove(name, cancelled));
        if (tasks.putIfAbsent(name, managedTask) != null) {
            throw new IllegalStateException("task name already scheduled: " + name);
        }

        try {
            ScheduledFuture<?> future = executor.scheduleWithFixedDelay(
                    managedTask,
                    startingDelay.toNanos(),
                    recurringDelay.toNanos(),
                    TimeUnit.NANOSECONDS);
            managedTask.attach(future);
            return managedTask;
        } catch (RuntimeException | Error failure) {
            managedTask.cancel();
            throw failure;
        }
    }

    void cancelAll() {
        new ArrayList<>(tasks.values()).forEach(ManagedScheduledTask::cancel);
    }

    boolean cancelAllAndAwait(long deadline) {
        boolean stopped = true;
        for (ManagedScheduledTask task : new ArrayList<>(tasks.values())) {
            if (!task.cancelAndAwait(deadline)) {
                stopped = false;
            }
        }
        return stopped;
    }

    void rejectNewTasks() {
        acceptingTasks.set(false);
    }

    int taskCount() {
        return tasks.size();
    }

    private static String requireTaskName(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName must not be blank");
        }
        return taskName;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
