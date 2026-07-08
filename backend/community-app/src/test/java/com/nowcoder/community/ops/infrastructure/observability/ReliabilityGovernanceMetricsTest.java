package com.nowcoder.community.ops.infrastructure.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityGovernanceMetricsTest {

    @Test
    void recordReplayShouldUseBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliabilityGovernanceMetrics metrics = new ReliabilityGovernanceMetrics(registry);

        metrics.recordReplay("projection.search.post", "REPLAYED");
        metrics.recordReplay("projection.search.post", "REPLAYED");
        metrics.recordReplay("projection.growth.task.post", "MANUAL_REPAIR_REQUIRED");

        assertThat(registry.counter(
                "community_outbox_replay_total",
                "topic", "projection.search.post",
                "result", "REPLAYED"
        ).count()).isEqualTo(2.0);
        assertThat(registry.counter(
                "community_outbox_replay_total",
                "topic", "projection.growth.task.post",
                "result", "MANUAL_REPAIR_REQUIRED"
        ).count()).isEqualTo(1.0);
    }

    @Test
    void recordGovernanceMetricsShouldUseBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliabilityGovernanceMetrics metrics = new ReliabilityGovernanceMetrics(registry);

        metrics.recordOutboxBatchReplay("projection.search.post", "PARTIAL", 3);
        metrics.recordGovernanceAction("OUTBOX_REPLAY_BATCH", "PARTIAL");
        metrics.recordHotCacheGovernance("PREWARM", "ACCEPTED", "global");
        metrics.recordCompensationTrigger("outboxRecoverExpiredLeases", "ACCEPTED");

        assertThat(registry.counter(
                "community_outbox_batch_replay_total",
                "topic", "projection.search.post",
                "result", "PARTIAL"
        ).count()).isEqualTo(3.0);
        assertThat(registry.counter(
                "community_governance_action_total",
                "action", "OUTBOX_REPLAY_BATCH",
                "result", "PARTIAL"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_hot_cache_governance_total",
                "operation", "PREWARM",
                "result", "ACCEPTED",
                "scope", "global"
        ).count()).isEqualTo(1.0);
        assertThat(registry.counter(
                "community_compensation_trigger_total",
                "job.name", "outboxRecoverExpiredLeases",
                "result", "ACCEPTED"
        ).count()).isEqualTo(1.0);
    }
}
