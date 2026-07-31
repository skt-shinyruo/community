package com.nowcoder.yierloom.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.example.yierloom.integration.AgentTargetMain;
import com.nowcoder.yierloom.integration.support.ForkResult;
import com.nowcoder.yierloom.integration.support.ForkedJvm;
import com.nowcoder.yierloom.integration.support.TestPluginJarBuilder;
import com.nowcoder.yierloom.testkit.PluginContractVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalPluginIT {
    private static final Path AGENT_JAR = Path.of("target", "yierloom-agent.jar")
            .toAbsolutePath()
            .normalize();

    @TempDir
    Path tempDir;

    @Test
    void addingACombinedPluginActivatesItWithoutChangingTheAgentJar() throws Exception {
        TestPluginJarBuilder plugins = pluginBuilder();
        Path pluginDirectory = Files.createDirectories(tempDir.resolve("combined-plugins"));
        String checksumBefore = sha256(AGENT_JAR);
        Path pluginJar = plugins.combinedObservationPlugin(
                pluginDirectory, "external-sample", "private-v1");

        PluginContractVerifier.verifyOrThrow(pluginJar);
        ForkResult result = runWithPluginDirectory(pluginDirectory, "combined-plugin-it");
        String checksumAfter = sha256(AGENT_JAR);

        assertThat(checksumAfter).isEqualTo(checksumBefore);
        assertSuccessfulFork(result);
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"external-sample\"",
                "\"event.action\":\"external_sample_summary\"",
                "\"fixture.private.version\":\"private-v1\"",
                "\"seen\":1");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"external-sample\"",
                "\"fixture.target.class\":\"com.example.yierloom.integration.AgentTargetService\"",
                "\"fixture.bridge.loader\":\"bootstrap\"");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"external-sample\"",
                "\"fixture.target.class\":\"com.example.yierloom.integration.CustomLoadedTarget\"",
                "\"fixture.helper.loader\":"
                        + "\"com.example.yierloom.integration.CustomTargetClassLoader\"",
                "\"fixture.bridge.loader\":\"bootstrap\"");
    }

    @Test
    void runtimeOnlyAndInstrumentationOnlyProvidersUseTheRootPluginSpi() throws Exception {
        TestPluginJarBuilder plugins = pluginBuilder();
        Path pluginDirectory = Files.createDirectories(tempDir.resolve("capability-plugins"));
        plugins.runtimePlugin(pluginDirectory, "runtime-only", "runtime");
        plugins.instrumentationOnlyPlugin(pluginDirectory, "instrumentation-only");

        ForkResult result = runWithPluginDirectory(pluginDirectory, "capability-plugin-it");

        assertSuccessfulFork(result);
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"runtime-only\"",
                "\"event.action\":\"runtime_only_ready\"",
                "\"fixture.private.version\":\"runtime\"");
        assertEventLine(
                result.stdout(),
                "\"diagnostic.plugin.id\":\"instrumentation-only\"",
                "\"event.action\":\"instrumentation_only_hit\"");
    }

    private static ForkResult runWithPluginDirectory(Path pluginDirectory, String serviceName)
            throws Exception {
        return ForkedJvm.run(
                AGENT_JAR,
                externalPluginArguments(pluginDirectory, serviceName),
                AgentTargetMain.class);
    }

    private TestPluginJarBuilder pluginBuilder() throws IOException {
        return new TestPluginJarBuilder(tempDir.resolve("plugin-builder"));
    }

    private static String externalPluginArguments(Path pluginDirectory, String serviceName) {
        return String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=" + serviceName,
                "yierloom.plugins.dir=" + pluginDirectory.toAbsolutePath().normalize(),
                "yierloom.plugins.method.enabled=false",
                "yierloom.plugins.exception.enabled=false",
                "yierloom.plugins.thread.enabled=false",
                "yierloom.plugins.jvm.enabled=false");
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is unavailable", impossible);
        }
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void assertSuccessfulFork(ForkResult result) {
        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        assertThat(result.stdout()).contains("TARGET_COMPLETED");
        assertThat(result.combinedOutput()).doesNotContain(
                "disabled after bootstrap failure",
                "disabled after engine failure",
                "Exception in thread",
                "NoClassDefFoundError",
                "LinkageError");
    }

    private static void assertEventLine(String stdout, String... fragments) {
        assertThat(stdout.lines().filter(line -> line.startsWith("{")).toList())
                .anySatisfy(line -> assertThat(line).contains(fragments));
    }
}
