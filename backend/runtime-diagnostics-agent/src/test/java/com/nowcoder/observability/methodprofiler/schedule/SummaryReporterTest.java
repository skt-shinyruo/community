package com.nowcoder.observability.methodprofiler.schedule;

import com.nowcoder.observability.methodprofiler.log.ProfilerEventLogger;
import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.stats.MethodLatencyAggregator;
import com.nowcoder.observability.methodprofiler.trace.TraceContextReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryReporterTest {

    @Test
    void reportsTopSnapshots() {
        MethodLatencyAggregator aggregator = new MethodLatencyAggregator(10);
        aggregator.record(new MethodKey("com.example.Service", "work", "abcdef1234567890"), 42);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SummaryReporter reporter = new SummaryReporter(
                aggregator,
                new ProfilerEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8)),
                new TraceContextReader(),
                5
        );

        reporter.reportOnce();

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.Service\"");
    }
}
