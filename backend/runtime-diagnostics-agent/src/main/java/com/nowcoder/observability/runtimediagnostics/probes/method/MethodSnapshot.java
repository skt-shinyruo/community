package com.nowcoder.observability.runtimediagnostics.probes.method;

public record MethodSnapshot(
        MethodKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms
) {
}
