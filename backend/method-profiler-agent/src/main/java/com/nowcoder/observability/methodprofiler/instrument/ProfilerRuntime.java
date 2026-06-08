package com.nowcoder.observability.methodprofiler.instrument;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.rate.TokenBucketRateLimiter;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;

import java.util.concurrent.ThreadLocalRandom;

public final class ProfilerRuntime {

    private static volatile ProfilerConfig config;
    private static volatile MethodLatencyAggregator aggregator;
    private static volatile ProfilerEventLogger logger;
    private static volatile TraceContextReader traceReader;
    private static volatile TokenBucketRateLimiter slowCallLimiter;

    private ProfilerRuntime() {
    }

    public static void initialize(ProfilerConfig profilerConfig, MethodLatencyAggregator latencyAggregator) {
        config = profilerConfig;
        aggregator = latencyAggregator;
        logger = new ProfilerEventLogger();
        traceReader = new TraceContextReader();
        slowCallLimiter = new TokenBucketRateLimiter(profilerConfig.maxEventsPerSecond(), System::nanoTime);
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
        MethodKey key = MethodKey.from(className, methodName, descriptor);
        currentAggregator.record(key, durationMs);
        if (durationMs >= currentConfig.slowThresholdMs()) {
            ProfilerEventLogger currentLogger = logger;
            TraceContextReader currentTraceReader = traceReader;
            TokenBucketRateLimiter limiter = slowCallLimiter;
            if (currentLogger != null && currentTraceReader != null && limiter != null && limiter.tryAcquire()) {
                currentLogger.logSlowCall(key, durationMs, currentConfig.slowThresholdMs(), currentTraceReader.currentTraceFields());
            }
        }
    }

    static void resetForTests() {
        config = null;
        aggregator = null;
        logger = null;
        traceReader = null;
        slowCallLimiter = null;
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
}
