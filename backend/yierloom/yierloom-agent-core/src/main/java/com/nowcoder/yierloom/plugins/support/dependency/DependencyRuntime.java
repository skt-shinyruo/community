package com.nowcoder.yierloom.plugins.support.dependency;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.ObservationHandler;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.plugins.support.DependencyTextSanitizer;
import com.nowcoder.yierloom.plugins.support.SamplingDecider;
import com.nowcoder.yierloom.plugins.support.TokenBucketRateLimiter;

public final class DependencyRuntime implements ObservationHandler, AutoCloseable {
    private static final String OBSERVATION_TYPE = "dependency-call";
    private static final List<String> TRACE_FIELDS = List.of("trace.id", "span.id");

    private final PluginRuntimeContext context;
    private final String pluginId;
    private final String slowAction;
    private final String summaryAction;
    private final List<String> dimensionKeys;
    private final long slowThresholdMs;
    private final int topN;
    private final DependencyCallAggregator aggregator;
    private final SamplingDecider sampling;
    private final TokenBucketRateLimiter slowCallLimiter;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Object emissionLock = new Object();
    private volatile ManagedTask summaryTask;

    private DependencyRuntime(
            PluginRuntimeContext context,
            String pluginId,
            String slowAction,
            String summaryAction,
            List<String> dimensionKeys,
            double sampleRate,
            int maxEventsPerSecond,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.pluginId = requireText(pluginId, "pluginId");
        this.slowAction = requireText(slowAction, "slowAction");
        this.summaryAction = requireText(summaryAction, "summaryAction");
        this.dimensionKeys = List.copyOf(Objects.requireNonNull(dimensionKeys, "dimensionKeys"));
        if (this.dimensionKeys.isEmpty()
                || this.dimensionKeys.stream().anyMatch(DependencyRuntime::invalidDimensionKey)
                || new LinkedHashSet<>(this.dimensionKeys).size() != this.dimensionKeys.size()) {
            throw new IllegalArgumentException("dimensionKeys contain an invalid or duplicate key");
        }
        if (slowThresholdMs < 0) {
            throw new IllegalArgumentException("slowThresholdMs must not be negative");
        }
        if (topN < 1) {
            throw new IllegalArgumentException("topN must be positive");
        }
        this.slowThresholdMs = slowThresholdMs;
        this.topN = topN;
        this.aggregator = new DependencyCallAggregator(maxTrackedKeys);
        this.sampling = new SamplingDecider(sampleRate);
        this.slowCallLimiter = new TokenBucketRateLimiter(maxEventsPerSecond);
    }

    public static DependencyRuntime start(
            PluginRuntimeContext context,
            String pluginId,
            String slowAction,
            String summaryAction,
            List<String> dimensionKeys,
            double sampleRate,
            int maxEventsPerSecond,
            Duration summaryInterval,
            int topN,
            int maxTrackedKeys,
            long slowThresholdMs
    ) {
        Objects.requireNonNull(summaryInterval, "summaryInterval");
        DependencyRuntime runtime = new DependencyRuntime(
                context,
                pluginId,
                slowAction,
                summaryAction,
                dimensionKeys,
                sampleRate,
                maxEventsPerSecond,
                topN,
                maxTrackedKeys,
                slowThresholdMs);
        try {
            context.observations().register(runtime);
            runtime.summaryTask = context.scheduler().scheduleWithFixedDelay(
                    pluginId + "-summary",
                    summaryInterval,
                    summaryInterval,
                    runtime::reportSummary);
            return runtime;
        } catch (Throwable failure) {
            throw runtime.startFailure(failure);
        }
    }

    @Override
    public void onObservation(PluginObservation observation) {
        if (!active.get()
                || observation == null
                || !OBSERVATION_TYPE.equals(observation.type())
                || !sampling.sample()) {
            return;
        }

        long durationMs = Math.max(
                0,
                observation.longFields().getOrDefault("duration.ms", 0L));
        boolean error = observation.booleanFields().getOrDefault("error", false);
        DependencyCallKey key = new DependencyCallKey(
                pluginId,
                sanitizedDimensions(observation.attributes()));
        aggregator.record(key, durationMs, error);
        if (durationMs >= slowThresholdMs && slowCallLimiter.tryAcquire()) {
            emitSlowCall(key, durationMs, observation.attributes());
        }
    }

    public void reportSummary() {
        if (!active.get()) {
            return;
        }
        for (DependencyCallSnapshot snapshot : aggregator.topSnapshots(topN)) {
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(summaryAction)
                    .attribute("event.outcome", "success")
                    .longField("call.count", snapshot.count())
                    .longField("duration.avg.ms", snapshot.avgMs())
                    .longField("duration.max.ms", snapshot.maxMs())
                    .longField("duration.p95.ms", snapshot.p95Ms())
                    .longField("error.count", snapshot.errorCount());
            copyDimensions(event, snapshot.key().dimensions());
            emitIfActive(event.build());
        }
    }

    private void emitSlowCall(
            DependencyCallKey key,
            long durationMs,
            Map<String, String> observationAttributes
    ) {
        DiagnosticEvent.Builder event = DiagnosticEvent.builder(slowAction)
                .attribute("event.outcome", "threshold")
                .longField("duration.ms", durationMs)
                .longField("threshold.ms", slowThresholdMs);
        copyDimensions(event, key.dimensions());
        for (String traceField : TRACE_FIELDS) {
            String value = observationAttributes.get(traceField);
            if (value != null && !value.isBlank()) {
                event.attribute(traceField, DependencyTextSanitizer.dimension(value));
            }
        }
        emitIfActive(event.build());
    }

    private Map<String, String> sanitizedDimensions(Map<String, String> attributes) {
        Map<String, String> dimensions = new LinkedHashMap<>();
        for (String key : dimensionKeys) {
            dimensions.put(key, DependencyTextSanitizer.dimension(attributes.get(key)));
        }
        return dimensions;
    }

    private static void copyDimensions(
            DiagnosticEvent.Builder event,
            Map<String, String> dimensions
    ) {
        dimensions.forEach(event::attribute);
    }

    private void emitIfActive(DiagnosticEvent event) {
        synchronized (emissionLock) {
            if (active.get()) {
                context.events().emit(event);
            }
        }
    }

    @Override
    public void close() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        ManagedTask current = summaryTask;
        summaryTask = null;
        if (current != null) {
            current.cancel();
        }
        synchronized (emissionLock) {
            // Wait for an event admitted before the active flag changed.
        }
    }

    private RuntimeException startFailure(Throwable failure) {
        try {
            close();
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(pluginId + " dependency runtime could not start", failure);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static boolean invalidDimensionKey(String key) {
        return key == null
                || key.isBlank()
                || key.equals("@timestamp")
                || key.equals("service.name")
                || key.startsWith("event.")
                || key.startsWith("diagnostic.");
    }
}
