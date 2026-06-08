package com.nowcoder.observability.methodprofiler.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerConfigLoaderTest {

    @Test
    void defaultsAreDisabledAndBounded() {
        ProfilerConfig config = ProfilerConfigLoader.load("", Map.of(), Map.of());

        assertThat(config.enabled()).isFalse();
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.excludes()).isEmpty();
        assertThat(config.slowThresholdMs()).isEqualTo(100);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.topN()).isEqualTo(50);
        assertThat(config.sampleRate()).isEqualTo(1.0);
        assertThat(config.maxEventsPerSecond()).isEqualTo(20);
        assertThat(config.maxTrackedMethods()).isEqualTo(10_000);
    }

    @Test
    void environmentOverridesDefaults() {
        ProfilerConfig config = ProfilerConfigLoader.load("", Map.of(), Map.of(
                "METHOD_PROFILER_ENABLED", "true",
                "METHOD_PROFILER_INCLUDES", "com.example.*,org.demo.Service",
                "METHOD_PROFILER_EXCLUDES", "com.example.internal.*",
                "METHOD_PROFILER_SLOW_THRESHOLD_MS", "250",
                "METHOD_PROFILER_SUMMARY_INTERVAL", "30s",
                "METHOD_PROFILER_TOP_N", "25",
                "METHOD_PROFILER_SAMPLE_RATE", "0.25",
                "METHOD_PROFILER_MAX_EVENTS_PER_SECOND", "7",
                "METHOD_PROFILER_MAX_TRACKED_METHODS", "99"
        ));

        assertThat(config.enabled()).isTrue();
        assertThat(config.includes()).containsExactly("com.example.*", "org.demo.Service");
        assertThat(config.excludes()).containsExactly("com.example.internal.*");
        assertThat(config.slowThresholdMs()).isEqualTo(250);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.topN()).isEqualTo(25);
        assertThat(config.sampleRate()).isEqualTo(0.25);
        assertThat(config.maxEventsPerSecond()).isEqualTo(7);
        assertThat(config.maxTrackedMethods()).isEqualTo(99);
    }

    @Test
    void agentArgsOverrideEnvironment() {
        ProfilerConfig config = ProfilerConfigLoader.load(
                "enabled=true,includes=com.agent.*,slowThresholdMs=333,summaryInterval=5s,topN=3,sampleRate=0.5,maxEventsPerSecond=2,maxTrackedMethods=10",
                Map.of(),
                Map.of("METHOD_PROFILER_ENABLED", "false", "METHOD_PROFILER_INCLUDES", "com.env.*")
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.includes()).containsExactly("com.agent.*");
        assertThat(config.slowThresholdMs()).isEqualTo(333);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.topN()).isEqualTo(3);
        assertThat(config.sampleRate()).isEqualTo(0.5);
        assertThat(config.maxEventsPerSecond()).isEqualTo(2);
        assertThat(config.maxTrackedMethods()).isEqualTo(10);
    }
}
