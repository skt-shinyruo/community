package com.nowcoder.observability.runtimediagnostics.probes.redis;

import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyCallKey;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyDiagnosticsRuntime;
import com.nowcoder.observability.runtimediagnostics.probes.dependency.DependencyTextSanitizer;
import net.bytebuddy.asm.Advice;

import java.util.Locale;
import java.util.Map;

public class RedisTemplateAdvice {

    public static String commandName(String methodName) {
        return methodName == null || methodName.isBlank()
                ? "UNKNOWN"
                : methodName.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase(Locale.ROOT);
    }

    public static String hashKeyspace(String key) {
        return DependencyTextSanitizer.hash16(key);
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    static long onEnter() {
        return System.nanoTime();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    static void onExit(
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] arguments,
            @Advice.Enter long startedAtNanos,
            @Advice.Thrown Throwable thrown
    ) {
        long durationMs = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        DependencyDiagnosticsRuntime.recordCall(
                "redis",
                "redis_slow_call",
                "redis_call_summary",
                thrown == null ? "success" : "error",
                new DependencyCallKey("redis", Map.of(
                        "redis.command", commandName(methodName),
                        "redis.keyspace.hash", hashKeyspace(safeKeyspace(arguments))
                )),
                durationMs,
                DependencyDiagnosticsRuntime.thresholdMs("redis"),
                thrown != null,
                Map.of()
        );
    }

    private static String safeKeyspace(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof String value && !value.isBlank()) {
                int separator = value.indexOf(':');
                return separator > 0 ? value.substring(0, separator) : value;
            }
        }
        return null;
    }
}
