package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEvent;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.rate.TokenBucketRateLimiter;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class DependencyDiagnosticsRuntime {

    private static final int SLOW_EVENT_QUEUE_CAPACITY = 1024;
    private static final Map<String, String> RESERVED_EVENT_FIELDS = Map.of(
            "@timestamp", "@timestamp",
            "event.category", "event.category",
            "event.action", "event.action",
            "event.outcome", "event.outcome",
            "diagnostic.agent.name", "diagnostic.agent.name",
            "diagnostic.probe", "diagnostic.probe"
    );

    private static volatile DiagnosticsConfig config;
    private static volatile DiagnosticEventLogger logger;
    private static volatile DependencyCallAggregator aggregator;
    private static volatile Map<String, TokenBucketRateLimiter> slowCallLimiters;
    private static volatile BlockingQueue<SlowDependencyEvent> slowCallEvents;
    private static volatile Thread slowCallReporter;
    private static volatile Supplier<Map<String, String>> traceFieldSupplier = new TraceContextReader()::currentTraceFields;

    private DependencyDiagnosticsRuntime() {
    }

    public static void initialize(DiagnosticsConfig diagnosticsConfig, DiagnosticEventLogger eventLogger) {
        interruptSlowCallReporter();
        config = diagnosticsConfig;
        logger = eventLogger;
        aggregator = diagnosticsConfig == null
                ? null
                : new DependencyCallAggregator(diagnosticsConfig.maxTrackedKeys());
        slowCallLimiters = diagnosticsConfig == null ? Map.of() : Map.of(
                "http", new TokenBucketRateLimiter(diagnosticsConfig.httpMaxEventsPerSecond(), System::nanoTime),
                "jdbc", new TokenBucketRateLimiter(diagnosticsConfig.jdbcMaxEventsPerSecond(), System::nanoTime),
                "redis", new TokenBucketRateLimiter(diagnosticsConfig.redisMaxEventsPerSecond(), System::nanoTime),
                "kafka", new TokenBucketRateLimiter(diagnosticsConfig.kafkaMaxEventsPerSecond(), System::nanoTime)
        );
        slowCallEvents = new ArrayBlockingQueue<>(SLOW_EVENT_QUEUE_CAPACITY);
        traceFieldSupplier = new TraceContextReader()::currentTraceFields;
        if (eventLogger != null) {
            startSlowCallReporter(slowCallEvents, eventLogger);
        }
    }

    public static void recordCall(
            String probe,
            String slowAction,
            String summaryAction,
            String outcome,
            DependencyCallKey key,
            long durationMs,
            long thresholdMs,
            boolean error,
            Map<String, String> forbiddenDataForTests
    ) {
        DiagnosticsConfig currentConfig = config;
        DependencyCallAggregator currentAggregator = aggregator;
        if (currentConfig == null
                || currentAggregator == null
                || !currentConfig.enabled()
                || !currentConfig.probeEnabled(probe)
                || key == null) {
            return;
        }
        if (!sample(sampleRate(probe, currentConfig))) {
            return;
        }
        currentAggregator.record(key, durationMs, error);
        BlockingQueue<SlowDependencyEvent> events = slowCallEvents;
        if (events != null && durationMs >= thresholdMs && acquireSlowCallPermit(probe)) {
            events.offer(new SlowDependencyEvent(
                    probe,
                    slowAction,
                    key,
                    Math.max(0, durationMs),
                    Math.max(0, thresholdMs),
                    currentTraceFields()
            ));
        }
    }

    public static void reportSummary(String probe, String summaryAction, int topN) {
        DiagnosticsConfig currentConfig = config;
        DependencyCallAggregator currentAggregator = aggregator;
        DiagnosticEventLogger currentLogger = logger;
        if (currentConfig == null
                || currentAggregator == null
                || currentLogger == null
                || !currentConfig.enabled()
                || !currentConfig.probeEnabled(probe)) {
            return;
        }
        for (DependencyCallSnapshot snapshot : currentAggregator.topSnapshots(probe, topN)) {
            currentLogger.log(withDimensions(DiagnosticEvent.builder(summaryAction, "success", probe)
                    .put("call.count", snapshot.count())
                    .put("duration.avg.ms", snapshot.avgMs())
                    .put("duration.max.ms", snapshot.maxMs())
                    .put("duration.p95.ms", snapshot.p95Ms())
                    .put("error.count", snapshot.errorCount()), snapshot.key().dimensions())
                    .build());
        }
    }

    public static long thresholdMs(String probe) {
        DiagnosticsConfig currentConfig = config;
        if (currentConfig == null) {
            return Long.MAX_VALUE;
        }
        return switch (probe) {
            case "http" -> currentConfig.httpSlowThresholdMs();
            case "jdbc" -> currentConfig.jdbcSlowThresholdMs();
            case "redis" -> currentConfig.redisSlowThresholdMs();
            case "kafka" -> currentConfig.kafkaSlowThresholdMs();
            default -> Long.MAX_VALUE;
        };
    }

    public static void resetForTests() {
        interruptSlowCallReporter();
        config = null;
        logger = null;
        aggregator = null;
        slowCallLimiters = null;
        slowCallEvents = null;
        traceFieldSupplier = new TraceContextReader()::currentTraceFields;
    }

    private static DiagnosticEvent.Builder withDimensions(
            DiagnosticEvent.Builder builder,
            Map<String, String> dimensions
    ) {
        if (dimensions != null) {
            dimensions.forEach((key, value) -> {
                if (!RESERVED_EVENT_FIELDS.containsKey(key)) {
                    builder.put(key, value);
                }
            });
        }
        return builder;
    }

    static void replaceTraceContextReaderForTests(Supplier<Map<String, String>> replacement) {
        traceFieldSupplier = replacement == null ? new TraceContextReader()::currentTraceFields : replacement;
    }

    static void drainSlowEventsForTests() {
        BlockingQueue<SlowDependencyEvent> events = slowCallEvents;
        DiagnosticEventLogger currentLogger = logger;
        if (events == null || currentLogger == null) {
            return;
        }
        SlowDependencyEvent event;
        while ((event = events.poll()) != null) {
            logSlowEvent(currentLogger, event);
        }
    }

    private static boolean acquireSlowCallPermit(String probe) {
        Map<String, TokenBucketRateLimiter> limiters = slowCallLimiters;
        TokenBucketRateLimiter limiter = limiters == null ? null : limiters.get(probe);
        return limiter != null && limiter.tryAcquire();
    }

    private static double sampleRate(String probe, DiagnosticsConfig currentConfig) {
        return switch (probe) {
            case "http" -> currentConfig.httpSampleRate();
            case "jdbc" -> currentConfig.jdbcSampleRate();
            case "redis" -> currentConfig.redisSampleRate();
            case "kafka" -> currentConfig.kafkaSampleRate();
            default -> 0.0;
        };
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

    private static Map<String, String> currentTraceFields() {
        Supplier<Map<String, String>> supplier = traceFieldSupplier;
        if (supplier == null) {
            return Map.of();
        }
        try {
            Map<String, String> fields = supplier.get();
            return fields == null ? Map.of() : fields;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static void startSlowCallReporter(
            BlockingQueue<SlowDependencyEvent> events,
            DiagnosticEventLogger eventLogger
    ) {
        Thread reporter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    logSlowEvent(eventLogger, events.take());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }, "runtime-diagnostics-dependency-slow-calls");
        reporter.setDaemon(true);
        reporter.start();
        slowCallReporter = reporter;
    }

    private static void logSlowEvent(DiagnosticEventLogger eventLogger, SlowDependencyEvent event) {
        eventLogger.log(withDimensions(DiagnosticEvent.builder(event.slowAction(), "threshold", event.probe())
                .put("duration.ms", event.durationMs())
                .put("threshold.ms", event.thresholdMs()), event.key().dimensions())
                .putTraceFields(event.traceFields())
                .build());
    }

    private static void interruptSlowCallReporter() {
        Thread reporter = slowCallReporter;
        if (reporter != null) {
            reporter.interrupt();
        }
        slowCallReporter = null;
    }

    private record SlowDependencyEvent(
            String probe,
            String slowAction,
            DependencyCallKey key,
            long durationMs,
            long thresholdMs,
            Map<String, String> traceFields
    ) {
    }
}
