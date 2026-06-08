package com.nowcoder.observability.runtimediagnostics.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticEventLoggerTest {

    @Test
    void writesRuntimeDiagnosticsBaseFieldsAndTraceFields() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "community-app");

        logger.log(DiagnosticEvent.builder("method_slow_call", "threshold", "method")
                .put("duration.ms", 123)
                .putTraceFields(Map.of("trace.id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .build());

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.outcome\":\"threshold\"")
                .contains("\"diagnostic.agent.name\":\"runtime-diagnostics-agent\"")
                .contains("\"diagnostic.probe\":\"method\"")
                .contains("\"service.name\":\"community-app\"")
                .contains("\"trace.id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"")
                .contains("\"duration.ms\":123");
    }

    @Test
    void escapesJsonControlCharacters() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "svc");

        logger.log(DiagnosticEvent.builder("exception_observed", "error", "exception")
                .put("exception.type", "java.lang.IllegalStateException")
                .put("method.name", "line\nbreak")
                .build());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"method.name\":\"line\\nbreak\"");
    }

    @Test
    void writesNonFiniteNumbersAsStrings() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "svc");

        logger.log(DiagnosticEvent.builder("jvm_summary", "success", "jvm")
                .put("runtime.value", Double.NaN)
                .build());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"runtime.value\":\"NaN\"");
    }

    @Test
    void loggerServiceNameCannotBeOverriddenByEventField() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticEventLogger logger = new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "logger-service");

        logger.log(DiagnosticEvent.builder("method_slow_call", "threshold", "method")
                .put("service.name", "event-service")
                .build());

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"service.name\":\"logger-service\"")
                .doesNotContain("event-service");
    }
}
