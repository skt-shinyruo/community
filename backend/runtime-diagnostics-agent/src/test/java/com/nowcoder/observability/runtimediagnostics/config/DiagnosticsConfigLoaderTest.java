package com.nowcoder.observability.runtimediagnostics.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticsConfigLoaderTest {

    @Test
    void defaultsAreDisabledAndPhaseOneProbesAreBounded() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of());

        assertThat(config.enabled()).isFalse();
        assertThat(config.probes()).containsExactly("method", "exception", "thread", "jvm");
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.excludes()).isEmpty();
        assertThat(config.sampleRate()).isEqualTo(1.0);
        assertThat(config.maxEventsPerSecond()).isEqualTo(20);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.topN()).isEqualTo(50);
        assertThat(config.maxTrackedKeys()).isEqualTo(10_000);
        assertThat(config.methodSlowThresholdMs()).isEqualTo(100);
        assertThat(config.threadSnapshotInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.jvmSummaryInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(config.probeEnabled("method")).isTrue();
        assertThat(config.probeEnabled("http")).isFalse();
    }

    @Test
    void runtimeDiagnosticsEnvironmentOverridesDefaults() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.ofEntries(
                Map.entry("RUNTIME_DIAGNOSTICS_ENABLED", "true"),
                Map.entry("RUNTIME_DIAGNOSTICS_PROBES", "method,jvm"),
                Map.entry("RUNTIME_DIAGNOSTICS_INCLUDES", "com.example.*,org.demo.Service"),
                Map.entry("RUNTIME_DIAGNOSTICS_EXCLUDES", "com.example.internal.*"),
                Map.entry("RUNTIME_DIAGNOSTICS_SAMPLE_RATE", "0.25"),
                Map.entry("RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND", "7"),
                Map.entry("RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL", "30s"),
                Map.entry("RUNTIME_DIAGNOSTICS_TOP_N", "25"),
                Map.entry("RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS", "99"),
                Map.entry("RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS", "250"),
                Map.entry("RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL", "15s"),
                Map.entry("RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL", "20s")
        ));

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("method", "jvm");
        assertThat(config.includes()).containsExactly("com.example.*", "org.demo.Service");
        assertThat(config.excludes()).containsExactly("com.example.internal.*");
        assertThat(config.sampleRate()).isEqualTo(0.25);
        assertThat(config.maxEventsPerSecond()).isEqualTo(7);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.topN()).isEqualTo(25);
        assertThat(config.maxTrackedKeys()).isEqualTo(99);
        assertThat(config.methodSlowThresholdMs()).isEqualTo(250);
        assertThat(config.threadSnapshotInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(config.jvmSummaryInterval()).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void dependencyProbeSettingsAreLoadedFromEnvironment() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.ofEntries(
                Map.entry("RUNTIME_DIAGNOSTICS_PROBES", "method,http,jdbc,redis,kafka"),
                Map.entry("RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS", "501"),
                Map.entry("RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS", "202"),
                Map.entry("RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS", "103"),
                Map.entry("RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS", "504"),
                Map.entry("RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE", "0.5"),
                Map.entry("RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE", "0.25"),
                Map.entry("RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE", "0.75"),
                Map.entry("RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE", "0.6"),
                Map.entry("RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND", "11"),
                Map.entry("RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND", "12"),
                Map.entry("RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND", "13"),
                Map.entry("RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND", "14"),
                Map.entry("RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED", "true")
        ));

        assertThat(config.probes()).containsExactly("method", "http", "jdbc", "redis", "kafka");
        assertThat(config.httpSlowThresholdMs()).isEqualTo(501);
        assertThat(config.jdbcSlowThresholdMs()).isEqualTo(202);
        assertThat(config.redisSlowThresholdMs()).isEqualTo(103);
        assertThat(config.kafkaSlowThresholdMs()).isEqualTo(504);
        assertThat(config.httpSampleRate()).isEqualTo(0.5);
        assertThat(config.jdbcSampleRate()).isEqualTo(0.25);
        assertThat(config.redisSampleRate()).isEqualTo(0.75);
        assertThat(config.kafkaSampleRate()).isEqualTo(0.6);
        assertThat(config.httpMaxEventsPerSecond()).isEqualTo(11);
        assertThat(config.jdbcMaxEventsPerSecond()).isEqualTo(12);
        assertThat(config.redisMaxEventsPerSecond()).isEqualTo(13);
        assertThat(config.kafkaMaxEventsPerSecond()).isEqualTo(14);
        assertThat(config.kafkaTopicNamesEnabled()).isTrue();
    }

    @Test
    void oldMethodProfilerEnvironmentNamesAreIgnored() {
        String oldPrefix = "METHOD" + "_PROFILER_";
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of(
                oldPrefix + "ENABLED", "true",
                oldPrefix + "INCLUDES", "com.legacy.*",
                oldPrefix + "SLOW_THRESHOLD_MS", "1"
        ));

        assertThat(config.enabled()).isFalse();
        assertThat(config.includes()).containsExactly("*");
        assertThat(config.methodSlowThresholdMs()).isEqualTo(100);
    }

    @Test
    void agentArgsOverrideEnvironmentAndSupportCsvContinuations() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load(
                "enabled=true,probes=method,exception,includes=com.agent.*,org.demo.Service,methodSlowThresholdMs=333,summaryInterval=5s,topN=3,sampleRate=0.5,maxEventsPerSecond=2,maxTrackedKeys=10",
                Map.of(),
                Map.of("RUNTIME_DIAGNOSTICS_ENABLED", "false", "RUNTIME_DIAGNOSTICS_INCLUDES", "com.env.*")
        );

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("method", "exception");
        assertThat(config.includes()).containsExactly("com.agent.*", "org.demo.Service");
        assertThat(config.methodSlowThresholdMs()).isEqualTo(333);
        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.topN()).isEqualTo(3);
        assertThat(config.sampleRate()).isEqualTo(0.5);
        assertThat(config.maxEventsPerSecond()).isEqualTo(2);
        assertThat(config.maxTrackedKeys()).isEqualTo(10);
    }

    @Test
    void systemPropertiesUseRuntimeDiagnosticsPrefix() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(
                "runtime.diagnostics.enabled", "true",
                "runtime.diagnostics.probes", "thread,jvm",
                "runtime.diagnostics.topN", "9"
        ), Map.of());

        assertThat(config.enabled()).isTrue();
        assertThat(config.probes()).containsExactly("thread", "jvm");
        assertThat(config.topN()).isEqualTo(9);
    }

    @Test
    void bareEnvironmentAndSystemPropertyNamesAreIgnored() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(
                "enabled", "true",
                "topN", "1"
        ), Map.of(
                "enabled", "true",
                "RUNTIME_DIAGNOSTICS_ENABLED", "false"
        ));

        assertThat(config.enabled()).isFalse();
        assertThat(config.topN()).isEqualTo(50);
    }

    @Test
    void invalidFloatingPointValuesFallBackToDefaults() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(), Map.of(
                "RUNTIME_DIAGNOSTICS_SAMPLE_RATE", "NaN"
        ));

        assertThat(config.sampleRate()).isEqualTo(1.0);
    }

    @Test
    void dependencySampleRatesRejectNonFiniteDirectValues() {
        DiagnosticsConfig config = new DiagnosticsConfig(true, java.util.List.of("http", "jdbc", "redis", "kafka"),
                java.util.List.of("*"), java.util.List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.5,
                20, 20, 20, 20,
                false);

        assertThat(config.httpSampleRate()).isEqualTo(1.0);
        assertThat(config.jdbcSampleRate()).isEqualTo(1.0);
        assertThat(config.redisSampleRate()).isEqualTo(1.0);
        assertThat(config.kafkaSampleRate()).isEqualTo(0.5);
    }

    @Test
    void blankAgentArgListValueClearsLowerPrecedenceListValue() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load(
                "excludes=",
                Map.of(),
                Map.of("RUNTIME_DIAGNOSTICS_EXCLUDES", "com.env.*")
        );

        assertThat(config.excludes()).isEmpty();
    }

    @Test
    void blankSystemPropertyListValueClearsLowerPrecedenceListValue() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load(
                "",
                Map.of("runtime.diagnostics.excludes", ""),
                Map.of("RUNTIME_DIAGNOSTICS_EXCLUDES", "com.env.*")
        );

        assertThat(config.excludes()).isEmpty();
    }

    @Test
    void iso8601DurationsRemainSupported() {
        DiagnosticsConfig config = DiagnosticsConfigLoader.load("", Map.of(
                "runtime.diagnostics.summaryInterval", "PT30S"
        ), Map.of());

        assertThat(config.summaryInterval()).isEqualTo(Duration.ofSeconds(30));
    }
}
