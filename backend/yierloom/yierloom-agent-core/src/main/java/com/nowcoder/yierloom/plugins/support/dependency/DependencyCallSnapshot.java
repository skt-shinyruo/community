package com.nowcoder.yierloom.plugins.support.dependency;

public record DependencyCallSnapshot(
        DependencyCallKey key,
        long count,
        long avgMs,
        long maxMs,
        long p95Ms,
        long errorCount
) {
}
