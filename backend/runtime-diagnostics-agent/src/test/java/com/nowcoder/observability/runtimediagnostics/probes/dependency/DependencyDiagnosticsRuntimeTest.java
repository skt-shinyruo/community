package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyDiagnosticsRuntimeTest {

    @AfterEach
    void tearDown() {
        DependencyDiagnosticsRuntime.resetForTests();
    }

    @Test
    void recordsSlowCallWithoutPayload() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        DependencyDiagnosticsRuntime.recordCall(
                "jdbc",
                "jdbc_slow_call",
                "jdbc_call_summary",
                "threshold",
                new DependencyCallKey("jdbc", Map.of("db.operation", "select", "db.statement.hash", "abcdef")),
                250,
                200,
                true,
                Map.of("sql", "select * from user where password='secret'")
        );
        DependencyDiagnosticsRuntime.drainSlowEventsForTests();

        String json = awaitOutput(out, "jdbc_slow_call");
        assertThat(json)
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"jdbc_slow_call\"")
                .contains("\"event.outcome\":\"threshold\"")
                .contains("\"diagnostic.probe\":\"jdbc\"")
                .contains("\"db.operation\":\"select\"")
                .contains("\"db.statement.hash\":\"abcdef\"")
                .contains("\"duration.ms\":250")
                .doesNotContain("password")
                .doesNotContain("select *");
    }

    @Test
    void summaryAggregatesCallsByDependencyKey() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));
        DependencyCallKey key = new DependencyCallKey("redis", Map.of("redis.command", "GET"));

        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key,
                10, 100, false, Map.of());
        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key,
                30, 100, false, Map.of());
        DependencyDiagnosticsRuntime.reportSummary("redis", "redis_call_summary", 5);

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"redis_call_summary\"")
                .contains("\"call.count\":2")
                .contains("\"duration.max.ms\":30");
    }

    @Test
    void slowCallOutcomeIsThresholdRegardlessOfCallOutcome() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success",
                new DependencyCallKey("redis", Map.of("redis.command", "SET")), 150, 100, false, Map.of());
        DependencyDiagnosticsRuntime.drainSlowEventsForTests();

        assertThat(awaitOutput(out, "redis_slow_call"))
                .contains("\"event.action\":\"redis_slow_call\"")
                .contains("\"event.outcome\":\"threshold\"")
                .doesNotContain("\"event.outcome\":\"success\"");
    }

    @Test
    void slowCallsAreBoundedByPerProbeRateLimit() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(configWithRedisLimit(1),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));
        DependencyCallKey key = new DependencyCallKey("redis", Map.of("redis.command", "GET"));

        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key,
                150, 100, false, Map.of());
        DependencyDiagnosticsRuntime.recordCall("redis", "redis_slow_call", "redis_call_summary", "success", key,
                160, 100, false, Map.of());
        DependencyDiagnosticsRuntime.drainSlowEventsForTests();

        awaitOutput(out, "redis_slow_call");
        assertThat(occurrences(out.toString(StandardCharsets.UTF_8), "redis_slow_call"))
                .isEqualTo(1);
    }

    @Test
    void slowCallsPreserveTraceFields() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DependencyDiagnosticsRuntime.initialize(config(),
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));
        DependencyDiagnosticsRuntime.replaceTraceContextReaderForTests(() -> Map.of(
                "trace.id", "trace-123",
                "span.id", "span-456"
        ));

        DependencyDiagnosticsRuntime.recordCall("jdbc", "jdbc_slow_call", "jdbc_call_summary", "success",
                new DependencyCallKey("jdbc", Map.of("db.operation", "select")), 250, 200, false, Map.of());
        DependencyDiagnosticsRuntime.drainSlowEventsForTests();

        assertThat(awaitOutput(out, "trace-123"))
                .contains("\"trace.id\":\"trace-123\"")
                .contains("\"span.id\":\"span-456\"");
    }

    private static DiagnosticsConfig config() {
        return new DiagnosticsConfig(true, List.of("jdbc", "redis"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                1.0, 1.0, 1.0, 1.0,
                20, 20, 20, 20,
                false);
    }

    private static DiagnosticsConfig configWithRedisLimit(int redisMaxEventsPerSecond) {
        return new DiagnosticsConfig(true, List.of("redis"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10_000, 100, Duration.ofSeconds(60), Duration.ofSeconds(60),
                500, 200, 100, 500,
                1.0, 1.0, 1.0, 1.0,
                20, 20, redisMaxEventsPerSecond, 20,
                false);
    }

    private static String awaitOutput(ByteArrayOutputStream out, String expected) {
        long deadline = System.nanoTime() + 1_000_000_000L;
        String value = out.toString(StandardCharsets.UTF_8);
        while (!value.contains(expected) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            value = out.toString(StandardCharsets.UTF_8);
        }
        return value;
    }

    private static int occurrences(String value, String pattern) {
        return value.split(pattern, -1).length - 1;
    }
}
