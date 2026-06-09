package com.nowcoder.observability.runtimediagnostics.probes.dependency;

public record DependencyCallSnapshot(
        DependencyCallKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms,
        long errorCount
) {
}
