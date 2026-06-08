package com.nowcoder.observability.methodprofiler.model;

public record MethodSnapshot(
        MethodKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms
) {
}
