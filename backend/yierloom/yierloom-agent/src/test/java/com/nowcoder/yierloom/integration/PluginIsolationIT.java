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

class PluginIsolationIT {
    private static final Path AGENT_JAR = Path.of("target", "yierloom-agent.jar")
            .toAbsolutePath()
            .normalize();

    @TempDir
    Path tempDir;

    @Test
    void pluginsResolveConflictingPrivateDependencyVersionsFromTheirOwnJars() throws Exception {
        TestPluginJarBuilder plugins = new TestPluginJarBuilder(
                tempDir.resolve("plugin-builder"));
        Path pluginDirectory = Files.createDirectories(tempDir.resolve("plugins"));
        plugins.runtimePlugin(pluginDirectory, "conflict-one", "one");
        plugins.runtimePlugin(pluginDirectory, "conflict-two", "two");

        ForkResult result = ForkedJvm.run(
                AGENT_JAR,
                agentArguments(pluginDirectory),
                AgentTargetMain.class);

        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        assertThat(result.stdout()).contains("TARGET_COMPLETED");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"conflict-one\"",
                "\"event.action\":\"conflict_one_ready\"",
                "\"fixture.private.version\":\"one\"");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"conflict-two\"",
                "\"event.action\":\"conflict_two_ready\"",
                "\"fixture.private.version\":\"two\"");
        assertActiveStatus(result.stderr(), "conflict-one");
        assertActiveStatus(result.stderr(), "conflict-two");
        assertThat(result.combinedOutput()).doesNotContain(
                "reason=DUPLICATE_ID",
                "disabled after bootstrap failure",
                "disabled after engine failure",
                "Exception in thread",
                "NoClassDefFoundError",
                "LinkageError");
    }

    private static String agentArguments(Path pluginDirectory) {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=plugin-isolation-it",
                "yierloom.plugins.dir=" + pluginDirectory.toAbsolutePath().normalize(),
                "yierloom.plugins.method.enabled=false",
                "yierloom.plugins.exception.enabled=false",
                "yierloom.plugins.thread.enabled=false",
                "yierloom.plugins.jvm.enabled=false");
    }

    private static void assertActiveStatus(String stderr, String pluginId) {
        assertThat(stderr.lines().toList()).anySatisfy(line -> assertThat(line).contains(
                "[YierLoom] plugin status:",
                "plugin=" + pluginId + ",",
                "enabled=true",
                "state=ACTIVE"));
    }

    private static void assertEventLine(String stdout, String... fragments) {
        assertThat(stdout.lines().filter(line -> line.startsWith("{")).toList())
                .anySatisfy(line -> assertThat(line).contains(fragments));
    }
}
