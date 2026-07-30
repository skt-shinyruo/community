package com.nowcoder.yierloom.plugins.method;

public record MethodSnapshot(
        MethodKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms
) {
}
