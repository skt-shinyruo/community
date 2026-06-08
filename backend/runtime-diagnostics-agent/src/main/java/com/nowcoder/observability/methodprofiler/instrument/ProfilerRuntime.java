package com.nowcoder.observability.methodprofiler.instrument;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.rate.TokenBucketRateLimiter;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ProfilerRuntime {

    private static final int SLOW_EVENT_QUEUE_CAPACITY = 1024;

    private static volatile ProfilerConfig config;
    private static volatile MethodLatencyAggregator aggregator;
    private static volatile ProfilerEventLogger logger;
    private static volatile TraceContextReader traceReader;
    private static volatile TokenBucketRateLimiter slowCallLimiter;
    private static volatile BlockingQueue<SlowCallEvent> slowCallEvents;
    private static volatile ConcurrentHashMap<String, MethodKey> methodKeyCache;
    private static volatile Thread slowCallReporter;

    private ProfilerRuntime() {
    }

    public static void initialize(ProfilerConfig profilerConfig, MethodLatencyAggregator latencyAggregator) {
        interruptSlowCallReporter();
        config = profilerConfig;
        aggregator = latencyAggregator;
        logger = new ProfilerEventLogger();
        traceReader = new TraceContextReader();
        slowCallLimiter = new TokenBucketRateLimiter(profilerConfig.maxEventsPerSecond(), System::nanoTime);
        slowCallEvents = new ArrayBlockingQueue<>(SLOW_EVENT_QUEUE_CAPACITY);
        methodKeyCache = new ConcurrentHashMap<>();
        startSlowCallReporter(slowCallEvents, logger);
    }

    public static void record(String className, String methodName, String descriptor, long durationMs) {
        ProfilerConfig currentConfig = config;
        MethodLatencyAggregator currentAggregator = aggregator;
        if (currentConfig == null || currentAggregator == null || className == null || methodName == null) {
            return;
        }
        if (!sample(currentConfig.sampleRate())) {
            return;
        }
        MethodKey key = methodKey(className, methodName, descriptor, currentConfig.maxTrackedMethods());
        currentAggregator.record(key, durationMs);
        if (key != null && durationMs >= currentConfig.slowThresholdMs()) {
            ProfilerEventLogger currentLogger = logger;
            TraceContextReader currentTraceReader = traceReader;
            TokenBucketRateLimiter limiter = slowCallLimiter;
            BlockingQueue<SlowCallEvent> events = slowCallEvents;
            if (currentLogger != null && currentTraceReader != null && limiter != null && events != null && limiter.tryAcquire()) {
                events.offer(new SlowCallEvent(key, durationMs, currentConfig.slowThresholdMs(), currentTraceReader.currentTraceFields()));
            }
        }
    }

    static void resetForTests() {
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

    private static MethodKey methodKey(String className, String methodName, String descriptor, int maxTrackedMethods) {
        ConcurrentHashMap<String, MethodKey> cache = methodKeyCache;
        if (cache == null) {
            return MethodKey.from(className, methodName, descriptor);
        }
        String rawKey = className + "#" + methodName + ":" + descriptor;
        MethodKey existing = cache.get(rawKey);
        if (existing != null) {
            return existing;
        }
        if (cache.size() >= Math.max(1, maxTrackedMethods)) {
            return null;
        }
        MethodKey created = MethodKey.from(className, methodName, descriptor);
        MethodKey raced = cache.putIfAbsent(rawKey, created);
        return raced == null ? created : raced;
    }

    private static void startSlowCallReporter(BlockingQueue<SlowCallEvent> events, ProfilerEventLogger eventLogger) {
        Thread reporter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SlowCallEvent event = events.take();
                    eventLogger.logSlowCall(event.key(), event.durationMs(), event.thresholdMs(), event.traceFields());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException ignored) {
                }
            }
        }, "method-profiler-slow-calls");
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
