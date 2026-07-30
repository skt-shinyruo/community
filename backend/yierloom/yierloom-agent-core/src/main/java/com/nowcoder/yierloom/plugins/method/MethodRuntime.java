package com.nowcoder.yierloom.plugins.method;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.nowcoder.yierloom.api.DiagnosticEvent;
import com.nowcoder.yierloom.api.ManagedTask;
import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.PluginRuntimeContext;
import com.nowcoder.yierloom.plugins.support.SamplingDecider;
import com.nowcoder.yierloom.plugins.support.TokenBucketRateLimiter;

public final class MethodRuntime implements AutoCloseable {
    private static final String OBSERVATION_TYPE = "method-call";

    private final PluginRuntimeContext context;
    private final MethodPlugin.MethodSettings settings;
    private final MethodLatencyAggregator aggregator;
    private final SamplingDecider sampling;
    private final TokenBucketRateLimiter slowCallLimiter;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final Object emissionLock = new Object();
    private volatile ManagedTask summaryTask;

    private MethodRuntime(
            PluginRuntimeContext context,
            MethodPlugin.MethodSettings settings
    ) {
        this.context = Objects.requireNonNull(context, "context");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.aggregator = new MethodLatencyAggregator(settings.maxTrackedKeys());
        this.sampling = new SamplingDecider(settings.sampleRate());
        this.slowCallLimiter = new TokenBucketRateLimiter(settings.maxEventsPerSecond());
    }

    public static MethodRuntime start(
            PluginRuntimeContext context,
            MethodPlugin.MethodSettings settings
    ) {
        MethodRuntime runtime = new MethodRuntime(context, settings);
        try {
            context.observations().register(runtime::onObservation);
            runtime.summaryTask = context.scheduler().scheduleWithFixedDelay(
                    "method-summary",
                    settings.summaryInterval(),
                    settings.summaryInterval(),
                    runtime::reportSummary);
            return runtime;
        } catch (Throwable failure) {
            throw runtime.startFailure(failure);
        }
    }

    private void onObservation(PluginObservation observation) {
        if (!active.get()
                || observation == null
                || !OBSERVATION_TYPE.equals(observation.type())) {
            return;
        }
        Map<String, String> attributes = observation.attributes();
        String className = attributes.get("method.class");
        String methodName = attributes.get("method.name");
        String descriptor = attributes.get("method.descriptor");
        Long duration = observation.longFields().get("duration.ms");
        if (className == null
                || methodName == null
                || descriptor == null
                || duration == null
                || !sampling.sample()) {
            return;
        }

        long safeDuration = Math.max(0, duration);
        MethodKey key = MethodKey.from(className, methodName, descriptor);
        if (!aggregator.record(key, safeDuration)) {
            return;
        }
        if (safeDuration >= settings.slowThresholdMs() && slowCallLimiter.tryAcquire()) {
            emitSlowCall(key, safeDuration, attributes);
        }
    }

    private void emitSlowCall(
            MethodKey key,
            long durationMs,
            Map<String, String> observationAttributes
    ) {
        DiagnosticEvent.Builder event = DiagnosticEvent.builder("method_slow_call")
                .attribute("event.outcome", "threshold")
                .attribute("method.class", key.className())
                .attribute("method.name", key.methodName())
                .attribute("method.signature.hash", key.signatureHash())
                .longField("duration.ms", durationMs)
                .longField("threshold.ms", settings.slowThresholdMs());
        copyTraceFields(event, observationAttributes);
        emitIfActive(event.build());
    }

    private void reportSummary() {
        if (!active.get()) {
            return;
        }
        long droppedKeys = aggregator.droppedKeys();
        for (MethodSnapshot snapshot : aggregator.topSnapshots(settings.topN())) {
            DiagnosticEvent.Builder event = DiagnosticEvent.builder("method_latency_summary")
                    .attribute("event.outcome", "success")
                    .attribute("method.class", snapshot.key().className())
                    .attribute("method.name", snapshot.key().methodName())
                    .attribute("method.signature.hash", snapshot.key().signatureHash())
                    .longField("method.invocation.count", snapshot.count())
                    .longField("duration.avg.ms", snapshot.avgMs())
                    .longField("duration.max.ms", snapshot.maxMs())
                    .longField("duration.p95.ms", snapshot.p95Ms());
            if (droppedKeys > 0) {
                event.longField("method.dropped.keys", droppedKeys);
            }
            emitIfActive(event.build());
        }
    }

    private void emitIfActive(DiagnosticEvent event) {
        synchronized (emissionLock) {
            if (active.get()) {
                context.events().emit(event);
            }
        }
    }

    private static void copyTraceFields(
            DiagnosticEvent.Builder event,
            Map<String, String> observationAttributes
    ) {
        String traceId = observationAttributes.get("trace.id");
        if (traceId != null && !traceId.isBlank()) {
            event.attribute("trace.id", traceId);
        }
        String spanId = observationAttributes.get("span.id");
        if (spanId != null && !spanId.isBlank()) {
            event.attribute("span.id", spanId);
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
        return new IllegalStateException("method runtime could not start", failure);
    }
}
