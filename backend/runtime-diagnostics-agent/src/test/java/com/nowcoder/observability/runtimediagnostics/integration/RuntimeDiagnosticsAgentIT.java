package com.nowcoder.observability.runtimediagnostics.integration;

import com.example.runtimediagnostics.integration.AgentTargetMain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosticsAgentIT {

    @Test
    void agentEmitsPhaseOneEventsWithoutChangingTargetExceptions() throws Exception {
        Path agentJar = Path.of("target", "runtime-diagnostics-agent-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(agentJar).exists();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-javaagent:" + agentJar + "=enabled=true,probes=method,exception,thread,jvm,includes=com.example.runtimediagnostics.integration.*,methodSlowThresholdMs=1,summaryInterval=1s,threadSnapshotInterval=1s,jvmSummaryInterval=1s,topN=5,maxEventsPerSecond=10");
        command.add("-cp");
        command.add(classpath);
        command.add(AgentTargetMain.class.getName());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            boolean killed = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(killed).isTrue();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited)
                .describedAs("forked target output:%n%s", output)
                .isTrue();
        assertThat(process.exitValue()).isEqualTo(0);
        assertThat(output)
                .contains("target exception propagated")
                .contains("\"event.category\":\"runtime_diagnostics\"")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"event.action\":\"exception_observed\"")
                .contains("\"event.action\":\"thread_snapshot\"")
                .contains("\"event.action\":\"jvm_runtime_summary\"")
                .contains("\"method.class\":\"com.example.runtimediagnostics.integration.AgentTargetService\"")
                .doesNotContain("password=secret");
    }
}
