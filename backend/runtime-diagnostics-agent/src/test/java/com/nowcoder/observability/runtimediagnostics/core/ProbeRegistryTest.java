package com.nowcoder.observability.runtimediagnostics.core;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeRegistryTest {

    @Test
    void startsEnabledProbesAndContinuesAfterFailure() {
        List<String> started = new ArrayList<>();
        Probe failing = new RecordingProbe("method", started, true);
        Probe healthy = new RecordingProbe("jvm", started, false);
        Probe disabled = new RecordingProbe("thread", started, false);
        ProbeRegistry registry = new ProbeRegistry(List.of(failing, healthy, disabled));

        registry.startEnabled(config(List.of("method", "jvm")), ProbeContext.noop());

        assertThat(started).containsExactly("method", "jvm");
        assertThat(registry.disabledProbeNames()).containsExactly("method");
    }

    @Test
    void stopsStartedProbesInReverseOrder() {
        List<String> started = new ArrayList<>();
        List<String> stopped = new ArrayList<>();
        Probe method = new RecordingProbe("method", started, stopped, false);
        Probe thread = new RecordingProbe("thread", started, stopped, false);
        Probe disabled = new RecordingProbe("jvm", started, stopped, false);
        ProbeRegistry registry = new ProbeRegistry(List.of(method, thread, disabled));

        registry.startEnabled(config(List.of("method", "thread")), ProbeContext.noop());
        registry.stopStarted();

        assertThat(started).containsExactly("method", "thread");
        assertThat(stopped).containsExactly("thread", "method");
    }

    private static DiagnosticsConfig config(List<String> probes) {
        return new DiagnosticsConfig(true, probes, List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }

    private record RecordingProbe(String name, List<String> started, List<String> stopped, boolean fail) implements Probe {
        RecordingProbe(String name, List<String> started, boolean fail) {
            this(name, started, new ArrayList<>(), fail);
        }

        @Override
        public void start(ProbeContext context) {
            started.add(name);
            if (fail) {
                throw new IllegalStateException("probe failed");
            }
        }

        @Override
        public void stop() {
            stopped.add(name);
        }
    }
}
