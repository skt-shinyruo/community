package com.nowcoder.observability.runtimediagnostics.probes.exception;

import com.nowcoder.observability.runtimediagnostics.config.DiagnosticsConfig;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionDiagnosticsProbeTest {

    @AfterEach
    void tearDown() {
        DiagnosticRuntime.resetForTests();
    }

    @Test
    void logsExceptionTypeWithoutMessageOrStackTrace() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiagnosticRuntime.initialize(config(), null,
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        DiagnosticRuntime.recordException(
                "com.example.SecretService",
                "fail",
                "()V",
                new IllegalStateException("password=secret")
        );

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"exception_observed\"")
                .contains("\"event.outcome\":\"error\"")
                .contains("\"diagnostic.probe\":\"exception\"")
                .contains("\"exception.type\":\"java.lang.IllegalStateException\"")
                .contains("\"method.class\":\"com.example.SecretService\"")
                .contains("\"method.name\":\"fail\"")
                .contains("\"method.signature.hash\"")
                .doesNotContain("password=secret")
                .doesNotContain("stackTrace");
    }

    private static DiagnosticsConfig config() {
        return new DiagnosticsConfig(true, List.of("exception"), List.of("*"), List.of(), 1.0, 20,
                Duration.ofSeconds(60), 50, 10, 100, Duration.ofSeconds(60), Duration.ofSeconds(60));
    }
}
