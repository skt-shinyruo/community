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
}
