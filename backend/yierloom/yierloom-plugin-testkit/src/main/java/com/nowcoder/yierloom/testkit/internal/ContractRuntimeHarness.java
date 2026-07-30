package com.nowcoder.yierloom.testkit.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import com.nowcoder.yierloom.api.RuntimeCapability;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.testkit.PluginViolation;
import com.nowcoder.yierloom.testkit.PluginViolationSeverity;

public final class ContractRuntimeHarness {
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final Object BRIDGE_COORDINATION = new Object();

    private ContractRuntimeHarness() {
    }

    public static List<PluginViolation> verify(
            String pluginId,
            RuntimeCapability runtime,
            PluginConfig config
    ) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(config, "config");
        FailureCollector failures = new FailureCollector();
        synchronized (BRIDGE_COORDINATION) {
            exercise(pluginId, runtime, config, failures);
        }
        failures.rethrowFatal();
        return failures.violations();
    }

    private static void exercise(
            String pluginId,
            RuntimeCapability runtime,
            PluginConfig config,
            FailureCollector failures
    ) {
        InMemoryScheduler scheduler = new InMemoryScheduler();
        ScopedEndpoint endpoint = new ScopedEndpoint(pluginId, failures);
        ObservationChannel observations = new ScopedObservationChannel(endpoint);
        PluginRuntimeContext context = new HarnessContext(
                config,
                scheduler,
                observations,
                endpoint::emitOwned,
                System.getLogger("com.nowcoder.yierloom.testkit.plugin." + pluginId),
                CLOCK);
        boolean installed = false;
        boolean startAttempted = false;
        boolean started = false;
        try {
            installed = YierLoomBridge.install(endpoint);
            if (!installed) {
                failures.add("bridge.install", "Bridge endpoint is already installed");
                return;
            }
            startAttempted = true;
            try {
                runtime.start(context);
                started = true;
            } catch (Throwable failure) {
                failures.capture("start", failure);
            }
            if (started) {
                probeObservations(pluginId, endpoint, failures);
            }
        } finally {
            if (startAttempted) {
                stop(runtime, 1, failures);
                stop(runtime, 2, failures);
            }
            try {
                scheduler.close();
            } catch (Throwable failure) {
                failures.capture("scheduler.cleanup", failure);
            }
            try {
                endpoint.close();
            } catch (Throwable failure) {
                failures.capture("observations.cleanup", failure);
            }
            if (installed) {
                try {
                    if (!YierLoomBridge.clear(endpoint)) {
                        failures.add("bridge.clear", "Owned Bridge endpoint could not be cleared");
                    }
                } catch (Throwable failure) {
                    failures.capture("bridge.clear", failure);
                }
            }
        }
    }

    private static void probeObservations(
            String pluginId,
            ScopedEndpoint endpoint,
            FailureCollector failures
    ) {
        PluginObservation observation = PluginObservation.builder("testkit-contract-probe").build();
        try {
            boolean accepted = YierLoomBridge.observe(pluginId, observation);
            if (endpoint.hasHandler() && !accepted) {
                failures.add("observation.owner", "Owner observation was rejected");
            }
        } catch (Throwable failure) {
            failures.capture("observation.owner", failure);
        }
        try {
            if (YierLoomBridge.observe(pluginId + "-foreign", observation)) {
                failures.add("observation.foreign", "Foreign observation was accepted");
            }
        } catch (Throwable failure) {
            failures.capture("observation.foreign", failure);
        }
    }

    private static void stop(
            RuntimeCapability runtime,
            int attempt,
            FailureCollector failures
    ) {
        try {
            runtime.stop();
        } catch (Throwable failure) {
            failures.capture("stop[" + attempt + "]", failure);
        }
    }

    private record HarnessContext(
            PluginConfig config,
            ManagedScheduler scheduler,
            ObservationChannel observations,
            EventSink events,
            System.Logger logger,
            Clock clock
    ) implements PluginRuntimeContext {
        private HarnessContext {
            Objects.requireNonNull(config);
            Objects.requireNonNull(scheduler);
            Objects.requireNonNull(observations);
            Objects.requireNonNull(events);
            Objects.requireNonNull(logger);
            Objects.requireNonNull(clock);
        }
    }

    private static final class ScopedObservationChannel implements ObservationChannel {
        private final ScopedEndpoint endpoint;

        private ScopedObservationChannel(ScopedEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public void register(ObservationHandler handler) {
            endpoint.register(handler);
        }
    }

    private static final class ScopedEndpoint implements YierLoomBridge.Endpoint {
        private final String pluginId;
        private final FailureCollector failures;
        private ObservationHandler handler;
        private boolean active = true;
        private int inFlightObservations;

        private ScopedEndpoint(String pluginId, FailureCollector failures) {
            this.pluginId = pluginId;
            this.failures = failures;
        }

        private synchronized void register(ObservationHandler candidate) {
            Objects.requireNonNull(candidate, "handler");
            if (!active) {
                throw new IllegalStateException("observation channel is closed");
            }
            if (handler != null) {
                throw new IllegalStateException("observation handler is already registered");
            }
            handler = candidate;
        }

        @Override
        public boolean observe(String candidatePluginId, PluginObservation observation) {
            ObservationHandler current;
            synchronized (this) {
                if (!active
                        || !pluginId.equals(candidatePluginId)
                        || observation == null
                        || handler == null) {
                    return false;
                }
                current = handler;
                inFlightObservations++;
            }
            try {
                current.onObservation(observation);
                return true;
            } catch (Throwable failure) {
                failures.capture("observation.handler", failure);
                return false;
            } finally {
                observationCompleted();
            }
        }

        @Override
        public synchronized boolean emit(String candidatePluginId, DiagnosticEvent event) {
            return active && pluginId.equals(candidatePluginId) && event != null;
        }

        private synchronized boolean emitOwned(DiagnosticEvent event) {
            return emit(pluginId, event);
        }

        private synchronized boolean hasHandler() {
            return handler != null;
        }

        private void close() {
            boolean interrupted = false;
            synchronized (this) {
                active = false;
                handler = null;
                while (inFlightObservations != 0) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private synchronized void observationCompleted() {
            inFlightObservations--;
            if (inFlightObservations == 0) {
                notifyAll();
            }
        }
    }

    private static final class InMemoryScheduler implements ManagedScheduler {
        private final Map<String, InMemoryTask> tasks = new LinkedHashMap<>();
        private boolean accepting = true;

        @Override
        public synchronized ManagedTask scheduleWithFixedDelay(
                String taskName,
                Duration initialDelay,
                Duration delay,
                Runnable task
        ) {
            if (!accepting) {
                throw new IllegalStateException("scheduler is closed");
            }
            if (taskName == null || taskName.isBlank()) {
                throw new IllegalArgumentException("task name must not be blank");
            }
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(delay, "delay");
            Objects.requireNonNull(task, "task");
            if (initialDelay.isNegative() || delay.isZero() || delay.isNegative()) {
                throw new IllegalArgumentException("invalid schedule delay");
            }
            if (tasks.containsKey(taskName)) {
                throw new IllegalArgumentException("duplicate task name");
            }
            InMemoryTask managed = new InMemoryTask(taskName, this);
            tasks.put(taskName, managed);
            return managed;
        }

        private synchronized boolean cancel(InMemoryTask task) {
            if (task.cancelled) {
                return false;
            }
            task.cancelled = true;
            tasks.remove(task.name, task);
            return true;
        }

        private void close() {
            List<InMemoryTask> remaining;
            synchronized (this) {
                accepting = false;
                remaining = new ArrayList<>(tasks.values());
            }
            remaining.forEach(InMemoryTask::cancel);
        }
    }

    private static final class InMemoryTask implements ManagedTask {
        private final String name;
        private final InMemoryScheduler owner;
        private volatile boolean cancelled;

        private InMemoryTask(String name, InMemoryScheduler owner) {
            this.name = name;
            this.owner = owner;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean cancel() {
            return owner.cancel(this);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class FailureCollector {
        private final List<PluginViolation> violations = new ArrayList<>();
        private Throwable fatal;

        private synchronized void capture(String location, Throwable failure) {
            Throwable fatalCause = fatalCause(failure);
            if (fatalCause != null) {
                if (fatal == null) {
                    fatal = fatalCause;
                }
                return;
            }
            violations.add(new PluginViolation(
                    PluginViolationSeverity.ERROR,
                    "LIFECYCLE_CONTRACT",
                    location,
                    failure == null ? "unknown failure" : failure.getClass().getName()));
        }

        private synchronized void add(String location, String detail) {
            violations.add(new PluginViolation(
                    PluginViolationSeverity.ERROR,
                    "LIFECYCLE_CONTRACT",
                    location,
                    detail));
        }

        private synchronized List<PluginViolation> violations() {
            return List.copyOf(violations);
        }

        private synchronized void rethrowFatal() {
            if (fatal instanceof VirtualMachineError error) {
                throw error;
            }
            if (fatal instanceof ThreadDeath error) {
                throw error;
            }
        }

        private static Throwable fatalCause(Throwable failure) {
            if (failure == null) {
                return null;
            }
            ArrayDeque<Throwable> pending = new ArrayDeque<>();
            Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            pending.add(failure);
            while (!pending.isEmpty()) {
                Throwable current = pending.removeFirst();
                if (!visited.add(current)) {
                    continue;
                }
                if (current instanceof VirtualMachineError || current instanceof ThreadDeath) {
                    return current;
                }
                enqueueCause(current, pending);
                enqueueSuppressed(current, pending);
            }
            return null;
        }

        private static void enqueueCause(
                Throwable current,
                ArrayDeque<Throwable> pending
        ) {
            try {
                enqueue(current.getCause(), pending);
            } catch (Throwable accessorFailure) {
                enqueue(accessorFailure, pending);
            }
        }

        private static void enqueueSuppressed(
                Throwable current,
                ArrayDeque<Throwable> pending
        ) {
            try {
                Throwable[] suppressed = current.getSuppressed();
                if (suppressed == null) {
                    return;
                }
                for (Throwable candidate : suppressed) {
                    enqueue(candidate, pending);
                }
            } catch (Throwable accessorFailure) {
                enqueue(accessorFailure, pending);
            }
        }

        private static void enqueue(
                Throwable candidate,
                ArrayDeque<Throwable> pending
        ) {
            if (candidate != null) {
                pending.addLast(candidate);
            }
        }
    }
}
