package com.nowcoder.observability.runtimediagnostics.probes.jvm;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticEventLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JvmDiagnosticsProbeTest {

    @Test
    void reportOnceLogsJvmRuntimeSummary() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JvmDiagnosticsProbe probe = new JvmDiagnosticsProbe(
                ManagementFactory.getRuntimeMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getGarbageCollectorMXBeans(),
                ManagementFactory.getClassLoadingMXBean(),
                ManagementFactory.getThreadMXBean()
        );

        probe.reportOnce(new DiagnosticEventLogger(new PrintStream(out, true, StandardCharsets.UTF_8), "test"));

        String json = out.toString(StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"event.action\":\"jvm_runtime_summary\"")
                .contains("\"event.outcome\":\"success\"")
                .contains("\"diagnostic.probe\":\"jvm\"")
                .contains("\"jvm.uptime.ms\":")
                .contains("\"jvm.memory.heap.used.bytes\":")
                .contains("\"jvm.thread.count\":")
                .contains("\"jvm.class.loaded.count\":")
                .contains("\"jvm.gc.collection.count\":");
    }
}
