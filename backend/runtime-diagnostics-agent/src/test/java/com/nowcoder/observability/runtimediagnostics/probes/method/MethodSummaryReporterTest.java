package com.nowcoder.observability.runtimediagnostics.probes.method;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import com.nowcoder.observability.runtimediagnostics.trace.TraceContextReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MethodSummaryReporterTest {

    @Test
    void reportsTopSnapshots() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        aggregator.record(new MethodKey("com.example.Service", "work", "abcdef1234567890"), 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MethodSummaryReporter reporter = new MethodSummaryReporter(
                aggregator,
                new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"),
                new TraceContextReader(),
                5
        );

        reporter.reportOnce();

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.Service\"");
    }
}
