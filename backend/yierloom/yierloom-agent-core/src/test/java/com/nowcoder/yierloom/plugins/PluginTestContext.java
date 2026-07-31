package com.nowcoder.yierloom.plugins;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ManagedScheduler;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.ObservationChannel;
import com.nowcoder.yierloom.api.ObservationHandler;
import com.nowcoder.yierloom.api.PluginConfig;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;

public final class PluginTestContext implements PluginRuntimeContext {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T10:15:30Z"), ZoneOffset.UTC);

    private final PluginConfig config;
    private final List<DiagnosticEvent> events = new ArrayList<>();
    private final Map<String, TestManagedTask> tasks = new LinkedHashMap<>();
    private ObservationHandler handler;

    public PluginTestContext(Map<String, String> config) {
        this.config = PluginConfig.of(config);
    }

    @Override
    public PluginConfig config() {
        return config;
    }

    @Override
    public ManagedScheduler scheduler() {
        return (name, initialDelay, delay, task) -> {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(task, "task");
            if (name.isBlank() || initialDelay.isNegative() || delay.isZero() || delay.isNegative()) {
                throw new IllegalArgumentException("invalid scheduled task");
            }
            TestManagedTask managed = new TestManagedTask(name, initialDelay, delay, task);
            if (tasks.putIfAbsent(name, managed) != null) {
                throw new IllegalStateException("duplicate task: " + name);
            }
            return managed;
        };
    }

    @Override
    public ObservationChannel observations() {
        return candidate -> {
            Objects.requireNonNull(candidate, "handler");
            if (handler != null) {
                throw new IllegalStateException("handler already registered");
            }
            handler = candidate;
        };
    }

    @Override
    public EventSink events() {
        return event -> {
            events.add(Objects.requireNonNull(event));
            return true;
        };
    }

    @Override
    public System.Logger logger() {
        return System.getLogger("com.nowcoder.yierloom.plugins.test");
    }

    @Override
    public Clock clock() {
        return CLOCK;
    }

    public void deliver(PluginObservation observation) throws Exception {
        if (handler == null) {
            throw new IllegalStateException("no observation handler");
        }
        handler.onObservation(observation);
    }

    public void runTask(String name) {
        TestManagedTask task = tasks.get(name);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + name);
        }
        task.run();
    }

    public List<DiagnosticEvent> emittedEvents() {
        return List.copyOf(events);
    }

    public List<DiagnosticEvent> events(String action) {
        return events.stream().filter(event -> action.equals(event.action())).toList();
    }

    public DiagnosticEvent singleEvent(String action) {
        List<DiagnosticEvent> matching = events(action);
        if (matching.size() != 1) {
            throw new AssertionError("expected one " + action + " event but found " + matching.size());
        }
        return matching.get(0);
    }

    public Set<String> scheduledTaskNames() {
        return Set.copyOf(tasks.keySet());
    }

    public Duration initialDelay(String name) {
        return task(name).initialDelay;
    }

    public Duration delay(String name) {
        return task(name).delay;
    }

    public boolean isTaskCancelled(String name) {
        return task(name).isCancelled();
    }

    private TestManagedTask task(String name) {
        TestManagedTask task = tasks.get(name);
        if (task == null) {
            throw new IllegalArgumentException("unknown task: " + name);
        }
        return task;
    }

    private static final class TestManagedTask implements ManagedTask {
        private final String name;
        private final Duration initialDelay;
        private final Duration delay;
        private final Runnable task;
        private boolean cancelled;

        private TestManagedTask(
                String name,
                Duration initialDelay,
                Duration delay,
                Runnable task
        ) {
            this.name = name;
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.task = task;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean cancel() {
            boolean changed = !cancelled;
            cancelled = true;
            return changed;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        private void run() {
            if (!cancelled) {
                task.run();
            }
        }
    }
}
