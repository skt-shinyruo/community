package com.nowcoder.observability.methodprofiler.log;

import com.nowcoder.observability.runtimediagnostics.probes.method.MethodKey;
import com.nowcoder.observability.runtimediagnostics.probes.method.MethodSnapshot;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilerEventLoggerTest {

    @Test
    void writesSummaryJsonWithoutArgumentsOrReturnValues() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProfilerEventLogger logger = new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8));
        MethodSnapshot snapshot = new MethodSnapshot(
                new MethodKey("com.example.SecretService", "findPassword", "abcdef1234567890"),
                3,
                10,
                30,
                20
        );

        logger.logSummary(List.of(snapshot), 0, Map.of("trace.id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"service.name\":")
                .contains("\"event.category\":\"method\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.SecretService\"")
                .contains("\"method.name\":\"findPassword\"")
                .contains("\"trace.id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"")
                .contains("\"duration.max.ms\":30")
                .doesNotContain("argument")
                .doesNotContain("return.value");
    }

    @Test
    void writesSlowCallJson() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProfilerEventLogger logger = new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8));

        logger.logSlowCall(new MethodKey("com.example.Service", "work", "abcdef1234567890"), 123, 100, Map.of());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"duration.ms\":123")
                .contains("\"threshold.ms\":100");
    }

    @Test
    void escapesJsonControlCharacters() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ProfilerEventLogger logger = new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8));

        logger.logSlowCall(new MethodKey("com.example.Service", "line\nbreak", "abcdef1234567890"), 123, 100, Map.of());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"method.name\":\"line\\nbreak\"");
    }
}
