package com.nowcoder.observability.methodprofiler.instrument;

import com.nowcoder.observability.methodprofiler.config.ProfilerConfig;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerRuntimeTest {

    @AfterEach
    void tearDown() {
        ProfilerRuntime.resetForTests();
    }

    @Test
    void recordsMethodDurationWhenConfigured() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        ProfilerRuntime.initialize(config(), aggregator);

        ProfilerRuntime.record("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1))
                .singleElement()
                .extracting(snapshot -> snapshot.key().className(), snapshot -> snapshot.key().methodName(), snapshot -> snapshot.maxMs())
                .containsExactly("com.example.Service", "work", 125L);
    }

    @Test
    void samplingZeroDropsRecords() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        ProfilerRuntime.initialize(new ProfilerConfig(true, List.of("*"), List.of(), 100, Duration.ofSeconds(60), 50, 0.0, 20, 10), aggregator);

        ProfilerRuntime.record("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1)).isEmpty();
    }

    private static ProfilerConfig config() {
        return new ProfilerConfig(true, List.of("*"), List.of(), 1_000, Duration.ofSeconds(60), 50, 1.0, 20, 10);
    }
}
