package com.nowcoder.observability.methodprofiler.integration;

import com.example.methodprofiler.integration.AgentTargetMain;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodProfilerAgentIT {

    @Test
    void agentEmitsSlowCallAndSummaryWithoutChangingTargetExceptions() throws Exception {
        Path agentJar = Path.of("target", "method-profiler-agent-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertThat(agentJar).exists();
        String classpath = System.getProperty("java.class.path");
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-javaagent:" + agentJar + "=enabled=true,includes=com.example.methodprofiler.integration.*,slowThresholdMs=1,summaryInterval=1s,topN=5,maxEventsPerSecond=10");
        command.add("-cp");
        command.add(classpath);
        command.add(AgentTargetMain.class.getName());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).isTrue();
        assertThat(process.exitValue()).isEqualTo(0);
        assertThat(output)
                .contains("target exception propagated")
                .contains("\"event.action\":\"method_slow_call\"")
                .contains("\"event.action\":\"method_latency_summary\"")
                .contains("\"method.class\":\"com.example.methodprofiler.integration.AgentTargetService\"");
    }
}
