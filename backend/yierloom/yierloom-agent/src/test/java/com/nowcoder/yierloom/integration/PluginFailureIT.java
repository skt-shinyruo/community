package com.nowcoder.yierloom.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.example.yierloom.integration.AgentTargetMain;
import com.nowcoder.yierloom.integration.support.ForkResult;
import com.nowcoder.yierloom.integration.support.ForkedJvm;
import com.nowcoder.yierloom.integration.support.TestPluginJarBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PluginFailureIT {
    private static final Path AGENT_JAR = Path.of("target", "yierloom-agent.jar")
            .toAbsolutePath()
            .normalize();

    @TempDir
    Path tempDir;

    @Test
    void brokenCandidatesAndPluginFailuresDoNotStopTheHostOrHealthyPlugins() throws Exception {
        TestPluginJarBuilder plugins = new TestPluginJarBuilder(
                tempDir.resolve("plugin-builder"));
        Path pluginDirectory = Files.createDirectories(tempDir.resolve("plugins"));
        plugins.corruptJar(pluginDirectory.resolve("00-corrupt.jar"));
        plugins.duplicateIdPair(pluginDirectory, "duplicate");
        plugins.startFailure(pluginDirectory, "start-failure");
        plugins.stopFailure(pluginDirectory, "stop-failure");
        plugins.invalidConfiguration(pluginDirectory, "bad-config");
        plugins.transformationFailure(pluginDirectory, "transform-failure");
        plugins.runtimePlugin(pluginDirectory, "healthy", "ok");

        ForkResult result = ForkedJvm.run(
                AGENT_JAR,
                agentArguments(pluginDirectory),
                AgentTargetMain.class);

        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        assertThat(result.stdout()).contains("TARGET_COMPLETED");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"healthy\"",
                "\"event.action\":\"healthy_ready\"",
                "\"fixture.private.version\":\"ok\"");
        assertLine(result.stderr(), "[YierLoom] discovery issue:", "reason=CANDIDATE_INVALID");
        assertDuplicateFailures(result.stderr());
        assertStatus(
                result.stderr(),
                "bad-config",
                "INVALID_CONFIG",
                "key=limit",
                "origin=AGENT_ARGUMENT@yierloom.plugins.bad-config.limit");
        assertStatus(result.stderr(), "start-failure", "ACTIVATION_FAILED");
        assertLine(
                result.combinedOutput(),
                "YierLoom transformation failure [plugin=transform-failure",
                "stage=transformer",
                "failure=java.lang.IllegalStateException");
        assertActiveStatus(result.stderr(), "healthy");
        assertActiveStatus(result.stderr(), "stop-failure");
        assertActiveStatus(result.stderr(), "transform-failure");
        assertLine(
                result.stderr(),
                "[YierLoom] plugin shutdown issue:",
                "plugin=stop-failure,",
                "state=STOPPED",
                "reason=RUNTIME_STOP_FAILED");
        assertThat(result.combinedOutput()).doesNotContain(
                "not-an-integer",
                "disabled after bootstrap failure",
                "disabled after engine failure",
                "Exception in thread",
                "NoClassDefFoundError",
                "LinkageError");
    }

    private static String agentArguments(Path pluginDirectory) {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=plugin-failure-it",
                "yierloom.plugins.dir=" + pluginDirectory.toAbsolutePath().normalize(),
                "yierloom.plugins.bad-config.limit=not-an-integer",
                "yierloom.plugins.method.enabled=false",
                "yierloom.plugins.exception.enabled=false",
                "yierloom.plugins.thread.enabled=false",
                "yierloom.plugins.jvm.enabled=false");
    }

    private static void assertDuplicateFailures(String stderr) {
        List<String> statuses = stderr.lines()
                .filter(line -> line.startsWith("[YierLoom] plugin status:"))
                .filter(line -> line.contains("plugin=duplicate,"))
                .toList();
        assertThat(statuses).hasSize(2).allSatisfy(line -> assertThat(line).contains(
                "enabled=false",
                "state=FAILED",
                "reason=DUPLICATE_ID"));
    }

    private static void assertStatus(
            String stderr,
            String pluginId,
            String reason,
            String... additionalFragments
    ) {
        String[] fragments = new String[additionalFragments.length + 4];
        fragments[0] = "[YierLoom] plugin status:";
        fragments[1] = "plugin=" + pluginId + ",";
        fragments[2] = "state=FAILED";
        fragments[3] = "reason=" + reason;
        System.arraycopy(additionalFragments, 0, fragments, 4, additionalFragments.length);
        assertLine(stderr, fragments);
    }

    private static void assertActiveStatus(String stderr, String pluginId) {
        assertLine(
                stderr,
                "[YierLoom] plugin status:",
                "plugin=" + pluginId + ",",
                "enabled=true",
                "state=ACTIVE");
    }

    private static void assertEventLine(String stdout, String... fragments) {
        assertThat(stdout.lines().filter(line -> line.startsWith("{")).toList())
                .anySatisfy(line -> assertThat(line).contains(fragments));
    }

    private static void assertLine(String output, String... fragments) {
        assertThat(output.lines().toList())
                .anySatisfy(line -> assertThat(line).contains(fragments));
    }
}
