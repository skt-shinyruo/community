package com.nowcoder.yierloom.core.event;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.EventSink;
import com.nowcoder.yierloom.api.ObservationChannel;
import com.nowcoder.yierloom.api.ObservationHandler;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;
import com.nowcoder.yierloom.core.config.YierLoomConfig;

public final class YierLoomEventPipeline implements YierLoomBridge.Endpoint {
    private static final String EXPORTER_NAME = "json-lines";
    private static final String EXPORT_STAGE = "write";
    private static final Duration MAX_DRAIN_TIMEOUT = Duration.ofSeconds(2);

    private final ArrayBlockingQueue<PipelineMessage> queue;
    private final ConcurrentMap<String, HandlerState> handlers = new ConcurrentHashMap<>();
    private final Set<String> activePlugins = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptingObservations = new AtomicBoolean(true);
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);
    private final DroppedMessages droppedMessages = new DroppedMessages();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicInteger pendingMessages = new AtomicInteger();
    private final Object admissionGate = new Object();
    private final Object drainMonitor = new Object();
    private final EventExporter exporter;
    private final ExporterFailureReporter failureReporter;
    private final Thread consumer;

    public YierLoomEventPipeline(YierLoomConfig config, Clock clock, PrintStream output) {
        this(config.eventQueueCapacity(), config.serviceName(), clock, output);
    }

    public YierLoomEventPipeline(int capacity, String serviceName, Clock clock, PrintStream output) {
        this(
                capacity,
                new JsonLineEventExporter(serviceName, clock, output),
                new ExporterFailureReporter(clock, System.err::println));
    }

    YierLoomEventPipeline(
            int capacity,
            EventExporter exporter,
            ExporterFailureReporter failureReporter
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.exporter = Objects.requireNonNull(exporter, "exporter");
        this.failureReporter = Objects.requireNonNull(failureReporter, "failureReporter");
        this.consumer = new Thread(this::consume, "yierloom-event-consumer");
        this.consumer.setDaemon(true);
    }

    public void registerPlugin(String pluginId) {
        activePlugins.add(requirePluginId(pluginId));
    }

    public void unregisterPlugin(String pluginId) {
        activePlugins.remove(pluginId);
        handlers.remove(pluginId);
    }

    public ObservationChannel observations(String pluginId) {
        String scopedPluginId = requirePluginId(pluginId);
        return handler -> registerHandler(scopedPluginId, handler);
    }

    public EventSink events(String pluginId) {
        String scopedPluginId = requirePluginId(pluginId);
        return event -> emit(scopedPluginId, event);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            consumer.start();
        }
    }

    @Override
    public boolean observe(String pluginId, PluginObservation observation) {
        pendingMessages.incrementAndGet();
        synchronized (admissionGate) {
            HandlerState handler = handlers.get(pluginId);
            if (!acceptingObservations.get()
                    || observation == null
                    || !activePlugins.contains(pluginId)
                    || handler == null) {
                messageFinished();
                return false;
            }
            return offer(new PipelineMessage.Observation(pluginId, observation));
        }
    }

    @Override
    public boolean emit(String pluginId, DiagnosticEvent event) {
        pendingMessages.incrementAndGet();
        synchronized (admissionGate) {
            if (!acceptingEvents.get() || event == null || !activePlugins.contains(pluginId)) {
                messageFinished();
                return false;
            }
            return offer(new PipelineMessage.Event(pluginId, event));
        }
    }

    public DroppedMessages droppedMessages() {
        return droppedMessages;
    }

    public void stopObservations() {
        synchronized (admissionGate) {
            acceptingObservations.set(false);
            handlers.clear();
        }
    }

    public boolean drainAndClose(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        Duration effectiveTimeout = timeout.compareTo(MAX_DRAIN_TIMEOUT) > 0
                ? MAX_DRAIN_TIMEOUT
                : timeout;
        long deadline = deadlineAfter(effectiveTimeout);
        synchronized (admissionGate) {
            acceptingObservations.set(false);
            handlers.clear();
            acceptingEvents.set(false);
        }
        boolean drained = awaitPendingMessages(deadline);
        if (!drained) {
            dropQueuedMessages();
        }

        consumer.interrupt();
        joinConsumer(deadline);
        return drained && pendingMessages.get() == 0 && !consumer.isAlive();
    }

    boolean consumerAlive() {
        return consumer.isAlive();
    }

    boolean hasHandler(String pluginId) {
        return handlers.containsKey(pluginId);
    }

    private void registerHandler(String pluginId, ObservationHandler handler) {
        Objects.requireNonNull(handler, "handler");
        synchronized (admissionGate) {
            if (!acceptingObservations.get() || !activePlugins.contains(pluginId)) {
                throw new IllegalStateException("plugin is not accepting observations: " + pluginId);
            }
            if (handlers.putIfAbsent(pluginId, new HandlerState(handler)) != null) {
                throw new IllegalStateException("observation handler already registered: " + pluginId);
            }
        }
    }

    private boolean offer(PipelineMessage message) {
        if (queue.offer(message)) {
            return true;
        }
        droppedMessages.increment(message);
        messageFinished();
        return false;
    }

    private void consume() {
        while (true) {
            try {
                PipelineMessage message = queue.take();
                try {
                    process(message);
                } finally {
                    messageFinished();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(PipelineMessage message) {
        if (message instanceof PipelineMessage.Observation observation) {
            notifyHandler(observation);
        } else if (message instanceof PipelineMessage.Event event) {
            export(event);
        }
    }

    private void notifyHandler(PipelineMessage.Observation observation) {
        HandlerState state = handlers.get(observation.pluginId());
        if (state == null) {
            return;
        }
        try {
            state.handler.onObservation(observation.value());
            state.consecutiveFailures.set(0);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            if (state.consecutiveFailures.incrementAndGet() >= 3) {
                handlers.remove(observation.pluginId(), state);
            }
        }
    }

    private void export(PipelineMessage.Event event) {
        try {
            exporter.export(event.pluginId(), event.value());
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            failureReporter.report(EXPORTER_NAME, EXPORT_STAGE, failure);
        }
    }

    private boolean awaitPendingMessages(long deadline) {
        synchronized (drainMonitor) {
            while (pendingMessages.get() != 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(drainMonitor, remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private void dropQueuedMessages() {
        List<PipelineMessage> leftovers = new ArrayList<>();
        queue.drainTo(leftovers);
        for (PipelineMessage leftover : leftovers) {
            droppedMessages.increment(leftover);
            messageFinished();
        }
    }

    private void joinConsumer(long deadline) {
        if (!started.get()) {
            return;
        }
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.timedJoin(consumer, remaining);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void messageFinished() {
        if (pendingMessages.decrementAndGet() == 0) {
            synchronized (drainMonitor) {
                drainMonitor.notifyAll();
            }
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

    private static String requirePluginId(String pluginId) {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        return pluginId;
    }

    private static final class HandlerState {
        private final ObservationHandler handler;
        private final AtomicInteger consecutiveFailures = new AtomicInteger();

        private HandlerState(ObservationHandler handler) {
            this.handler = handler;
        }
    }
}
