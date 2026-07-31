package com.nowcoder.yierloom.integration;

import java.nio.file.Files;
import java.nio.file.Path;

import com.example.yierloom.integration.AgentTargetMain;
import com.nowcoder.yierloom.integration.support.ForkResult;
import com.nowcoder.yierloom.integration.support.ForkedJvm;
import com.nowcoder.yierloom.integration.support.TestPluginJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AdviceVisibilityIT {
    private static final Path AGENT_JAR = Path.of("target", "yierloom-agent.jar")
            .toAbsolutePath()
            .normalize();

    @TempDir
    Path tempDir;

    @Test
    void adviceAndHelpersWorkAcrossSystemAndCustomClassLoadersWithOneBootstrapApiIdentity()
            throws Exception {
        ForkResult result = ForkedJvm.run(
                AGENT_JAR,
                enabledVisibilityArguments(),
                AgentTargetMain.class,
                "visibility");

        assertSuccessfulFork(result);
        assertThat(result.stdout()).contains(
                "CUSTOM_RESULT=custom-ok",
                "CUSTOM_TARGET_LOADER=custom",
                "CUSTOM_HELPER_LOADER=custom",
                "CUSTOM_API_LOADER=bootstrap",
                "FAST_RESULT=fast-ok",
                "SYSTEM_TARGET_LOADER=system",
                "SYSTEM_HELPER_LOADER=system",
                "SYSTEM_API_LOADER=bootstrap",
                "TARGET_COMPLETED");
        assertMethodObservation(result.stdout(), "CustomLoadedTarget");
        assertMethodObservation(result.stdout(), "AgentTargetService");
        assertThat(result.combinedOutput()).doesNotContain(
                "NoClassDefFoundError",
                "LinkageError",
                "ClassCastException",
                "loader constraint violation",
                "Exception in thread");
    }

    @Test
    void disabledAgentDoesNotStartPluginsTransformTargetsEmitEventsOrLeaveThreads()
            throws Exception {
        Path pluginDirectory = Files.createDirectories(tempDir.resolve("disabled-plugins"));
        Path startMarker = tempDir.resolve("provider-started.marker");
        Path transformationMarker = tempDir.resolve("target-transformed.marker");
        new TestPluginJarBuilder(tempDir.resolve("plugin-build"))
                .markerPlugin(
                        pluginDirectory,
                        "disabled-marker",
                        startMarker,
                        transformationMarker);

        ForkResult result = ForkedJvm.run(
                AGENT_JAR,
                String.join(",",
                        "yierloom.enabled=false",
                        "yierloom.plugins.dir=" + pluginDirectory.toAbsolutePath().normalize()),
                AgentTargetMain.class,
                "visibility");

        assertSuccessfulFork(result);
        assertThat(result.stdout()).contains(
                "CUSTOM_RESULT=custom-ok",
                "CUSTOM_HELPER_LOADER=missing",
                "FAST_RESULT=fast-ok",
                "SYSTEM_HELPER_LOADER=missing",
                "YIERLOOM_THREAD_COUNT=0",
                "TARGET_COMPLETED");
        assertThat(startMarker).doesNotExist();
        assertThat(transformationMarker).doesNotExist();
        assertThat(result.combinedOutput()).doesNotContain(
                "\"event.category\":\"yierloom\"",
                "\"event.action\":",
                "\"method.class\":",
                "[YierLoom] started:",
                "YierLoom transformation failure",
                "NoClassDefFoundError",
                "LinkageError",
                "Exception in thread");
    }

    private static String enabledVisibilityArguments() {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=advice-visibility-it",
                "yierloom.plugins.method.enabled=true",
                "yierloom.plugins.method.includes=com.example.yierloom.integration.*",
                "yierloom.plugins.method.sample-rate=1.0",
                "yierloom.plugins.method.max-events-per-second=100",
                "yierloom.plugins.method.summary-interval=20ms",
                "yierloom.plugins.method.slow-threshold=0ms",
                "yierloom.plugins.method.top-n=20",
                "yierloom.plugins.method.max-tracked-keys=100",
                "yierloom.plugins.exception.enabled=false",
                "yierloom.plugins.thread.enabled=false",
                "yierloom.plugins.jvm.enabled=false",
                "yierloom.plugins.http.enabled=false",
                "yierloom.plugins.jdbc.enabled=false",
                "yierloom.plugins.redis.enabled=false",
                "yierloom.plugins.kafka.enabled=false");
    }

    private static void assertMethodObservation(String stdout, String simpleClassName) {
        assertThat(stdout.lines().filter(line -> line.startsWith("{")).toList())
                .anySatisfy(line -> assertThat(line).contains(
                        "\"diagnostic.plugin.id\":\"method\"",
                        "\"event.action\":\"method_slow_call\"",
                        "\"method.class\":\"com.example.yierloom.integration."
                                + simpleClassName + "\""));
    }

    private static void assertSuccessfulFork(ForkResult result) {
        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
    }
}
