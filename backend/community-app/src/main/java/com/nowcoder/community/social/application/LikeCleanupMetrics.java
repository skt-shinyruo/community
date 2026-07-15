package com.nowcoder.community.social.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LikeCleanupMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong orphanTargets = new AtomicLong();
    private final AtomicLong cleanupLagSeconds = new AtomicLong();

    @Autowired
    public LikeCleanupMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    LikeCleanupMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            Gauge.builder("social_like_orphan_targets", orphanTargets, AtomicLong::doubleValue)
                    .register(meterRegistry);
            Gauge.builder("social_like_cleanup_lag_seconds", cleanupLagSeconds, AtomicLong::doubleValue)
                    .register(meterRegistry);
        }
    }

    static LikeCleanupMetrics noop() {
        return new LikeCleanupMetrics((MeterRegistry) null);
    }

    public void recordCleanup(String source, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "social_like_cleanup_total",
                Tags.of("source", bounded(source), "result", bounded(result))
        ).increment();
    }

    public void setOrphanTargets(long count) {
        orphanTargets.set(Math.max(0L, count));
    }

    public void recordCleanupLag(Duration lag) {
        long seconds = lag == null ? 0L : Math.max(0L, lag.toSeconds());
        cleanupLagSeconds.set(seconds);
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }
}
