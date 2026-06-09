package com.nowcoder.observability.runtimediagnostics.probes.thread;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadDiagnosticsProbeTest {

    @Test
    void reportOnceLogsAggregatedThreadSnapshot() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ThreadDiagnosticsProbe probe = new ThreadDiagnosticsProbe(ManagementFactory.getThreadMXBean());

        probe.reportOnce(new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"thread_snapshot\"")
                .contains("\"event.outcome\":\"snapshot\"")
                .contains("\"diagnostic.probe\":\"thread\"")
                .contains("\"thread.count\":")
                .contains("\"thread.state.runnable\":")
                .contains("\"thread.deadlock.count\":");
    }
}
