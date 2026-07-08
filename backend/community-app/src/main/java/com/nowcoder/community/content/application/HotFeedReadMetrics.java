package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class HotFeedReadMetrics {

    private final MeterRegistry meterRegistry;

    @Autowired
    public HotFeedReadMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    HotFeedReadMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String result, String scope) {
        recordCache("hot_feed", result, scope);
    }

    public void recordCache(String cache, String result, String scope) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "community_cache_requests_total",
                Tags.of(
                        "cache", bounded(cache),
                        "result", bounded(result),
                        "scope", bounded(scope)
                )
        ).increment();
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40);
    }
}
