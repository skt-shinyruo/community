package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodLatencyAggregator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticRuntimeTest {

    @AfterEach
    void tearDown() {
        DiagnosticRuntime.resetForTests();
    }

    @Test
    void recordsMethodDurationWhenMethodProbeIsEnabled() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        DiagnosticRuntime.initialize(config(1.0), aggregator, new DiagnosticEventLogger(System.out, "test"));

        DiagnosticRuntime.recordMethod("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1))
                .singleElement()
                .extracting(snapshot -> snapshot.key().className(), snapshot -> snapshot.key().methodName(), snapshot -> snapshot.maxMs())
                .containsExactly("com.example.Service", "work", 125L);
    }

    @Test
    void samplingZeroDropsMethodDurations() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        DiagnosticRuntime.initialize(config(0.0), aggregator, new DiagnosticEventLogger(System.out, "test"));

        DiagnosticRuntime.recordMethod("com.example.Service", "work", "()V", 125);

        assertThat(aggregator.topSnapshots(1)).isEmpty();
    }

    private static DiagnosticsConfig config(double sampleRate) {
        return new DiagnosticsConfig(true, List.of("method"), List.of("*"), List.of(), sampleRate, 20,
                Duration.ofSeconds(60), 50, 10, 1_000, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }
}
