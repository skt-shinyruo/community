package com.nowcoder.yierloom.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class YierLoomAgentPackagingIT {
    private static final Set<String> NESTED_LIBRARIES = Set.of(
            "META-INF/yierloom/lib/yierloom-plugin-api.jar",
            "META-INF/yierloom/lib/yierloom-bytebuddy-sdk.jar",
            "META-INF/yierloom/lib/yierloom-agent-core.jar",
            "META-INF/yierloom/lib/byte-buddy.jar");

    @TempDir
    Path tempDir;

    @Test
    void packagesOnlyTheJdkBootstrapAroundTheFixedUnshadedLibraries() throws Exception {
        Path artifact = Path.of("target", "yierloom-agent.jar").toAbsolutePath().normalize();

        try (JarFile jar = new JarFile(artifact.toFile())) {
            Attributes manifest = jar.getManifest().getMainAttributes();
            assertThat(manifest.getValue("Premain-Class"))
                    .isEqualTo("com.nowcoder.yierloom.bootstrap.YierLoomAgent");
            assertThat(manifest.getValue("Can-Redefine-Classes")).isEqualTo("false");
            assertThat(manifest.getValue("Can-Retransform-Classes")).isEqualTo("false");

            Set<String> nested = jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("META-INF/yierloom/lib/") && name.endsWith(".jar"))
                    .collect(Collectors.toSet());
            assertThat(nested).isEqualTo(NESTED_LIBRARIES);

            assertThat(jar.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.endsWith(".class")))
                    .allMatch(name -> name.startsWith("com/nowcoder/yierloom/bootstrap/"));
            assertThat(jar.getJarEntry("net/bytebuddy/ByteBuddy.class")).isNull();
            assertNestedByteBuddyIsUnrelocated(jar);
            assertNestedLibraryDoesNotBundleByteBuddy(
                    jar, "META-INF/yierloom/lib/yierloom-plugin-api.jar");
            assertNestedLibraryDoesNotBundleByteBuddy(
                    jar, "META-INF/yierloom/lib/yierloom-bytebuddy-sdk.jar");
            assertNestedLibraryDoesNotBundleByteBuddy(
                    jar, "META-INF/yierloom/lib/yierloom-agent-core.jar");
        }
    }

    @Test
    void launchesThePackagedAgentWithBootstrapOwnedApiTypes() throws Exception {
        Path artifact = Path.of("target", "yierloom-agent.jar").toAbsolutePath().normalize();
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path java = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        Process process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + artifact + "=yierloom.enabled=true",
                "-cp",
                testClasses.toString(),
                BootstrapApiProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains(
                "bridge-loader=bootstrap",
                "[YierLoom] started: discovered=8, enabled=4, disabled=4, active=4, failed=0");
        assertThat(output).doesNotContain(
                "disabled after bootstrap failure",
                "Exception in thread",
                "NoClassDefFoundError");
    }

    @Test
    void instrumentsMethodsAndExceptionsWithoutLeakingSensitiveFields() throws Exception {
        Path artifact = Path.of("target", "yierloom-agent.jar").toAbsolutePath().normalize();
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path java = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        String targetClass = "com.example.yierloomfixture.ObservedTarget";
        String arguments = String.join(",",
                "yierloom.enabled=true",
                "yierloom.service.name=plugin-instrumentation-it",
                "yierloom.plugins.method.includes=" + targetClass,
                "yierloom.plugins.method.slow-threshold=0ms",
                "yierloom.plugins.method.summary-interval=1h",
                "yierloom.plugins.exception.includes=" + targetClass);
        Process process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + artifact + "=" + arguments,
                "-cp",
                testClasses.toString(),
                "com.example.yierloomfixture.YierLoomPluginProbe")
                .redirectErrorStream(true)
                .start();

        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        List<String> lines = output.lines().toList();

        assertThat(exited).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("same-exception=true", "probe-finished=true");
        assertThat(lines).anySatisfy(line -> assertThat(line).contains(
                "\"event.action\":\"method_slow_call\"",
                "\"diagnostic.plugin.id\":\"method\"",
                "\"method.class\":\"" + targetClass + "\"",
                "\"method.name\":\"slowCall\""));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains(
                "\"event.action\":\"exception_observed\"",
                "\"diagnostic.plugin.id\":\"exception\"",
                "\"exception.type\":\"java.lang.IllegalStateException\"",
                "\"method.class\":\"" + targetClass + "\"",
                "\"method.name\":\"fail\""));
        assertThat(output).doesNotContain(
                "private-exception-message",
                "\"method.descriptor\"",
                "\"exception.message\"",
                "\"exception.stacktrace\"",
                "(Ljava/lang/IllegalStateException;)V");
    }

    @Test
    void rejectsAnApiTypePreloadedByTheSystemClassLoader() throws Exception {
        Path artifact = Path.of("target", "yierloom-agent.jar").toAbsolutePath().normalize();
        Path testClasses = Path.of("target", "test-classes").toAbsolutePath().normalize();
        Path apiJar = extractNestedApi(artifact);
        Path preloader = createPreloaderAgent();
        Path java = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        String classPath = testClasses + System.getProperty("path.separator") + apiJar;
        Process process = new ProcessBuilder(
                java.toString(),
                "-javaagent:" + preloader,
                "-javaagent:" + artifact + "=yierloom.enabled=true",
                "-cp",
                classPath,
                BootstrapApiProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains(
                "preloaded-api-loader=system",
                "[YierLoom] disabled after bootstrap failure: java.lang.IllegalStateException");
        assertThat(output).doesNotContain(
                "[YierLoom] started:",
                "Exception in thread");
    }

    @Test
    void outerBootstrapDependsOnlyOnJdkAgentModules() throws Exception {
        Path artifact = Path.of("target", "yierloom-agent.jar").toAbsolutePath().normalize();
        Path jdeps = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "jdeps.exe" : "jdeps");
        Process process = new ProcessBuilder(
                jdeps.toString(),
                "--print-module-deps",
                "--ignore-missing-deps",
                artifact.toString())
                .redirectErrorStream(true)
                .start();

        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output.trim()).isEqualTo("java.base,java.instrument");
    }

    private static void assertNestedByteBuddyIsUnrelocated(JarFile outer) throws IOException {
        try (InputStream input = outer.getInputStream(
                outer.getJarEntry("META-INF/yierloom/lib/byte-buddy.jar"));
             JarInputStream nested = new JarInputStream(input)) {
            Set<String> entries = nestedEntries(nested);
            assertThat(entries).contains("net/bytebuddy/ByteBuddy.class");
            assertThat(entries).noneMatch(name -> name.contains("/shaded/net/bytebuddy/"));
        }
    }

    private static void assertNestedLibraryDoesNotBundleByteBuddy(
            JarFile outer,
            String nestedLibrary
    ) throws IOException {
        try (InputStream input = outer.getInputStream(outer.getJarEntry(nestedLibrary));
             JarInputStream nested = new JarInputStream(input)) {
            assertThat(nestedEntries(nested)).noneMatch(name ->
                    name.startsWith("net/bytebuddy/")
                            || name.contains("/shaded/net/bytebuddy/"));
        }
    }

    private static Set<String> nestedEntries(JarInputStream nested) throws IOException {
        java.util.HashSet<String> entries = new java.util.HashSet<>();
        java.util.jar.JarEntry entry;
        while ((entry = nested.getNextJarEntry()) != null) {
            entries.add(entry.getName());
        }
        return Set.copyOf(entries);
    }

    private Path extractNestedApi(Path artifact) throws IOException {
        Path apiJar = tempDir.resolve("preloaded-yierloom-plugin-api.jar");
        try (JarFile outer = new JarFile(artifact.toFile());
             InputStream input = outer.getInputStream(
                     outer.getJarEntry("META-INF/yierloom/lib/yierloom-plugin-api.jar"))) {
            Files.copy(input, apiJar);
        }
        return apiJar;
    }

    private Path createPreloaderAgent() throws IOException {
        Path agent = tempDir.resolve("system-api-preloader.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(
                "Premain-Class", SystemApiPreloaderAgent.class.getName());
        String classEntry = SystemApiPreloaderAgent.class.getName().replace('.', '/') + ".class";
        try (InputStream input = SystemApiPreloaderAgent.class.getClassLoader()
                .getResourceAsStream(classEntry);
             JarOutputStream output = new JarOutputStream(Files.newOutputStream(agent), manifest)) {
            if (input == null) {
                throw new IOException("preloader agent class is unavailable");
            }
            output.putNextEntry(new java.util.jar.JarEntry(classEntry));
            input.transferTo(output);
            output.closeEntry();
        }
        return agent;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }
}

final class BootstrapApiProbe {
    private BootstrapApiProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        Class<?> bridge = Class.forName("com.nowcoder.yierloom.api.YierLoomBridge");
        String owner = bridge.getClassLoader() == null
                ? "bootstrap"
                : bridge.getClassLoader().getClass().getName();
        System.out.println("bridge-loader=" + owner);
    }
}
