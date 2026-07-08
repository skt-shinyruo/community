package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HotFeedReadMetricsTest {

    @Test
    void recordCacheShouldUseBoundedLabels() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.recordCache("post_detail", "singleflight_busy", "detail");

        Counter counter = registry.find("community_cache_requests_total")
                .tags("cache", "post_detail", "result", "singleflight_busy", "scope", "detail")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void existingHotFeedRecordShouldRemainCompatible() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        HotFeedReadMetrics metrics = new HotFeedReadMetrics(registry);

        metrics.record("fallback", "global");

        Counter counter = registry.find("community_cache_requests_total")
                .tags("cache", "hot_feed", "result", "fallback", "scope", "global")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
