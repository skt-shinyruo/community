package com.nowcoder.community.ops.infrastructure.observability;

import com.nowcoder.community.ops.application.GovernanceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReliabilityGovernanceMetrics implements GovernanceMetrics {

    private final MeterRegistry meterRegistry;

    public ReliabilityGovernanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordReplay(String topic, String result) {
        increment(
                "community_outbox_replay_total",
                Tags.of(
                        "topic", bounded(topic),
                        "result", bounded(result)
                ),
                1.0
        );
    }

    @Override
    public void recordOutboxBatchReplay(String topic, String result, long count) {
        increment(
                "community_outbox_batch_replay_total",
                Tags.of(
                        "topic", bounded(topic),
                        "result", bounded(result)
                ),
                Math.max(0L, count)
        );
    }

    @Override
    public void recordGovernanceAction(String action, String result) {
        increment(
                "community_governance_action_total",
                Tags.of(
                        "action", bounded(action),
                        "result", bounded(result)
                ),
                1.0
        );
    }

    @Override
    public void recordHotCacheGovernance(String operation, String result, String scope) {
        increment(
                "community_hot_cache_governance_total",
                Tags.of(
                        "operation", bounded(operation),
                        "result", bounded(result),
                        "scope", bounded(scope)
                ),
                1.0
        );
    }

    @Override
    public void recordCompensationTrigger(String jobName, String result) {
        increment(
                "community_compensation_trigger_total",
                Tags.of(
                        "job.name", bounded(jobName),
                        "result", bounded(result)
                ),
                1.0
        );
    }

    private void increment(String name, Tags tags, double amount) {
        if (meterRegistry == null || amount <= 0.0) {
            return;
        }
        meterRegistry.counter(name, tags).increment(amount);
    }

    private String bounded(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
