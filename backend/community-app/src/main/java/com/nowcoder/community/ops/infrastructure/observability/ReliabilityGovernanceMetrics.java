package com.nowcoder.community.ops.infrastructure.observability;

import com.nowcoder.community.ops.application.OutboxReplayMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReliabilityGovernanceMetrics implements OutboxReplayMetrics {

    private final MeterRegistry meterRegistry;

    public ReliabilityGovernanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordReplay(String topic, String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(
                "community_outbox_replay_total",
                Tags.of(
                        "topic", bounded(topic),
                        "result", bounded(result)
                )
        ).increment();
    }

    private String bounded(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
    }
}
