package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodKey;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodLatencyAggregator;
import com.nowcoder.observability.runtimediagnostics.rate.TokenBucketRateLimiter;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class DiagnosticRuntime {

    private static final int SLOW_EVENT_QUEUE_CAPACITY = 1024;

    private static volatile DiagnosticsConfig config;
    private static volatile MethodLatencyAggregator aggregator;
    private static volatile DiagnosticEventLogger logger;
    private static volatile TraceContextReader traceReader;
    private static volatile TokenBucketRateLimiter slowCallLimiter;
    private static volatile BlockingQueue<SlowCallEvent> slowCallEvents;
    private static volatile ConcurrentHashMap<String, MethodKey> methodKeyCache;
    private static volatile Thread slowCallReporter;

    private DiagnosticRuntime() {
    }

    public static void initialize(
            DiagnosticsConfig diagnosticsConfig,
            MethodLatencyAggregator latencyAggregator,
            DiagnosticEventLogger eventLogger
    ) {
        interruptSlowCallReporter();
        config = diagnosticsConfig;
        aggregator = latencyAggregator;
        logger = eventLogger;
        traceReader = new TraceContextReader();
        slowCallLimiter = diagnosticsConfig == null
                ? null
                : new TokenBucketRateLimiter(diagnosticsConfig.maxEventsPerSecond(), System::nanoTime);
        slowCallEvents = new ArrayBlockingQueue<>(SLOW_EVENT_QUEUE_CAPACITY);
        methodKeyCache = new ConcurrentHashMap<>();
        if (eventLogger != null) {
            startSlowCallReporter(slowCallEvents, eventLogger);
        }
    }

    public static void recordMethod(String className, String methodName, String descriptor, long durationMs) {
        DiagnosticsConfig currentConfig = config;
        MethodLatencyAggregator currentAggregator = aggregator;
        if (currentConfig == null
                || currentAggregator == null
                || !currentConfig.enabled()
                || !currentConfig.probeEnabled("method")
                || className == null
                || methodName == null) {
            return;
        }
        if (!sample(currentConfig.sampleRate())) {
            return;
        }
        MethodKey key = methodKey(className, methodName, descriptor, currentConfig.maxTrackedKeys());
        currentAggregator.record(key, durationMs);
        if (key != null && durationMs >= currentConfig.methodSlowThresholdMs()) {
            TraceContextReader currentTraceReader = traceReader;
            TokenBucketRateLimiter limiter = slowCallLimiter;
            BlockingQueue<SlowCallEvent> events = slowCallEvents;
            if (currentTraceReader != null && limiter != null && events != null && limiter.tryAcquire()) {
                events.offer(new SlowCallEvent(
                        key,
                        durationMs,
                        currentConfig.methodSlowThresholdMs(),
                        currentTraceReader.currentTraceFields()
                ));
            }
        }
    }

    public static void recordException(String className, String methodName, String descriptor, Throwable throwable) {
        DiagnosticsConfig currentConfig = config;
        DiagnosticEventLogger currentLogger = logger;
        if (currentConfig == null
                || currentLogger == null
                || throwable == null
                || !currentConfig.enabled()
                || !currentConfig.probeEnabled("exception")) {
            return;
        }
        if (!sample(currentConfig.sampleRate())) {
            return;
        }
        MethodKey key = methodKey(className, methodName, descriptor, currentConfig.maxTrackedKeys());
        if (key == null) {
            return;
        }
        TraceContextReader currentTraceReader = traceReader;
        currentLogger.log(DiagnosticEvent.builder("exception_observed", "error", "exception")
                .put("exception.type", throwable.getClass().getName())
                .put("method.class", key.className())
                .put("method.name", key.methodName())
                .put("method.signature.hash", key.signatureHash())
                .putTraceFields(currentTraceReader == null ? Map.of() : currentTraceReader.currentTraceFields())
                .build());
    }

    public static void resetForTests() {
        interruptSlowCallReporter();
        config = null;
        aggregator = null;
        logger = null;
        traceReader = null;
        slowCallLimiter = null;
        slowCallEvents = null;
        methodKeyCache = null;
    }

    private static boolean sample(double sampleRate) {
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private static MethodKey methodKey(String className, String methodName, String descriptor, int maxTrackedKeys) {
        ConcurrentHashMap<String, MethodKey> cache = methodKeyCache;
        if (cache == null) {
            return MethodKey.from(className, methodName, descriptor);
        }
        String rawKey = className + "#" + methodName + ":" + descriptor;
        MethodKey existing = cache.get(rawKey);
        if (existing != null) {
            return existing;
        }
        if (cache.size() >= Math.max(1, maxTrackedKeys)) {
            return null;
        }
        MethodKey created = MethodKey.from(className, methodName, descriptor);
        MethodKey raced = cache.putIfAbsent(rawKey, created);
        return raced == null ? created : raced;
    }

    private static void startSlowCallReporter(BlockingQueue<SlowCallEvent> events, DiagnosticEventLogger eventLogger) {
        Thread reporter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SlowCallEvent event = events.take();
                    eventLogger.log(DiagnosticEvent.builder("method_slow_call", "threshold", "method")
                            .put("method.class", event.key().className())
                            .put("method.name", event.key().methodName())
                            .put("method.signature.hash", event.key().signatureHash())
                            .put("duration.ms", event.durationMs())
                            .put("threshold.ms", event.thresholdMs())
                            .putTraceFields(event.traceFields())
                            .build());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }, "runtime-diagnostics-method-slow-calls");
        reporter.setDaemon(true);
        reporter.start();
        slowCallReporter = reporter;
    }

    private static void interruptSlowCallReporter() {
        Thread reporter = slowCallReporter;
        if (reporter != null) {
            reporter.interrupt();
        }
        slowCallReporter = null;
    }

    private record SlowCallEvent(MethodKey key, long durationMs, long thresholdMs, Map<String, String> traceFields) {
    }
}
