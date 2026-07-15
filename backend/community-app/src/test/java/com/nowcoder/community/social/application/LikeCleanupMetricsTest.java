package com.nowcoder.community.social.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LikeCleanupMetricsTest {

    @Test
    void cleanupReliabilityMetricsShouldExposeStableNamesAndBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LikeCleanupMetrics metrics = new LikeCleanupMetrics(registry);

        metrics.recordCleanup("content_event", "success");
        metrics.recordCleanup("content_event", "success");
        metrics.recordCleanup("reconciliation", "failed");
        metrics.setOrphanTargets(3L);
        metrics.recordCleanupLag(Duration.ofSeconds(12));

        assertThat(registry.counter(
                "social_like_cleanup_total",
                "source", "content_event",
                "result", "success"
        ).count()).isEqualTo(2.0);
        assertThat(registry.counter(
                "social_like_cleanup_total",
                "source", "reconciliation",
                "result", "failed"
        ).count()).isEqualTo(1.0);
        assertThat(registry.get("social_like_orphan_targets").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("social_like_cleanup_lag_seconds").gauge().value()).isEqualTo(12.0);
    }
}
