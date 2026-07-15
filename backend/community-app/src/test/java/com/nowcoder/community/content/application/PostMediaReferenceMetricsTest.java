package com.nowcoder.community.content.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostMediaReferenceMetricsTest {

    @Test
    void reconciliationMetricsShouldExposeStablePendingDriftAndOutcomeSeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PostMediaReferenceMetrics metrics = new PostMediaReferenceMetrics(registry);

        metrics.recordReconciliation("pending_command", "scheduled");
        metrics.recordReconciliation("pending_command", "scheduled");
        metrics.recordReconciliation("remote_query", "failed");
        metrics.setPendingReferences(3L);
        metrics.setDriftedReferences(2L);

        assertThat(registry.counter(
                "content_media_reference_reconciliation_total",
                "reason", "pending_command",
                "result", "scheduled"
        ).count()).isEqualTo(2.0);
        assertThat(registry.counter(
                "content_media_reference_reconciliation_total",
                "reason", "remote_query",
                "result", "failed"
        ).count()).isEqualTo(1.0);
        assertThat(registry.get("content_media_reference_pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("content_media_reference_drift").gauge().value()).isEqualTo(2.0);
    }
}
