package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class PostMediaReferenceMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingReferences = new AtomicLong();
    private final AtomicLong driftedReferences = new AtomicLong();

    @Autowired
    public PostMediaReferenceMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable());
    }

    PostMediaReferenceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        if (meterRegistry != null) {
            Gauge.builder("content_media_reference_pending", pendingReferences, AtomicLong::doubleValue)
                    .register(meterRegistry);
            Gauge.builder("content_media_reference_drift", driftedReferences, AtomicLong::doubleValue)
                    .register(meterRegistry);
        }
    }

    public void recordReconciliation(String reason, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "content_media_reference_reconciliation_total",
                Tags.of("reason", bounded(reason), "result", bounded(result))
        ).increment();
    }

    public void setPendingReferences(long count) {
        pendingReferences.set(Math.max(0L, count));
    }

    public void setDriftedReferences(long count) {
        driftedReferences.set(Math.max(0L, count));
    }

    private String bounded(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }
}
