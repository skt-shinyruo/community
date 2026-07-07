package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedReadApplicationServiceReliabilityTest {

    @Test
    void hotFeedReadMetricsShouldUseBoundedCacheTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.record("hit", "global");
        metrics.record("fallback", "board");
        metrics.record("degraded", "global");

        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "hit",
                "scope", "global"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "fallback",
                "scope", "board"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_cache_requests_total",
                "cache", "hot_feed",
                "result", "degraded",
                "scope", "global"
        ).count()).isEqualTo(1.0);
    }
}
