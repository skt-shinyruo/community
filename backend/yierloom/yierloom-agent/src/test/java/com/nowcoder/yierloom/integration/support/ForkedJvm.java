package com.nowcoder.yierloom.integration.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForkedJvm {
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final String YIERLOOM_ENV_PREFIX = "YIERLOOM_";
    private static final Set<String> AGENT_MODULES = Set.of(
            "yierloom-agent",
            "yierloom-agent-core",
            "yierloom-bytebuddy-sdk",
            "yierloom-plugin-api",
            "yierloom-plugin-testkit");
    private static final Set<String> AGENT_ARTIFACT_PREFIXES = Set.of(
            "yierloom-agent-",
            "yierloom-agent-core-",
            "yierloom-bytebuddy-sdk-",
            "yierloom-plugin-api-",
            "yierloom-plugin-testkit-",
            "byte-buddy-");

    private ForkedJvm() {
    }

    public static ForkResult run(
            Path agentJar,
            String agentArguments,
            Class<?> targetMain
    ) throws IOException, InterruptedException {
        return run(agentJar, agentArguments, targetMain, List.of(), Map.of(), List.of());
    }

    public static ForkResult run(
            Path agentJar,
            String agentArguments,
            Class<?> targetMain,
            String... targetArguments
    ) throws IOException, InterruptedException {
        return run(
                agentJar,
                agentArguments,
                targetMain,
                List.of(),
                Map.of(),
                List.of(targetArguments));
    }

    public static ForkResult run(
            Path agentJar,
            String agentArguments,
            Class<?> targetMain,
            Map<String, String> environment,
            String... targetArguments
    ) throws IOException, InterruptedException {
        return run(
                agentJar,
                agentArguments,
                targetMain,
                List.of(),
                environment,
                List.of(targetArguments));
    }

    public static ForkResult run(
            Path agentJar,
            String agentArguments,
            Class<?> targetMain,
            List<String> jvmArguments,
            Map<String, String> environment,
            List<String> targetArguments
    ) throws IOException, InterruptedException {
        Path artifact = requireReadableFile(agentJar, "agentJar");
        Class<?> mainClass = Objects.requireNonNull(targetMain, "targetMain");
        List<String> jvmOptions = immutableStrings(jvmArguments, "jvmArguments");
        Map<String, String> explicitEnvironment = Map.copyOf(
                Objects.requireNonNull(environment, "environment"));
        List<String> applicationArguments = immutableStrings(
                targetArguments, "targetArguments");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.addAll(jvmOptions);
        String javaAgent = "-javaagent:" + artifact;
        if (agentArguments != null && !agentArguments.isEmpty()) {
            javaAgent += "=" + agentArguments;
        }
        command.add(javaAgent);
        command.add("-cp");
        command.add(targetClasspath(artifact));
        command.add(mainClass.getName());
        command.addAll(applicationArguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(false);
        Map<String, String> childEnvironment = processBuilder.environment();
        childEnvironment.keySet().removeIf(ForkedJvm::isYierLoomEnvironmentVariable);
        childEnvironment.putAll(explicitEnvironment);

        Process process = processBuilder.start();
        ExecutorService drainers = null;
        Throwable primaryFailure = null;
        boolean restoreInterrupt = false;
        try {
            process.getOutputStream().close();
            drainers = Executors.newFixedThreadPool(
                    2, drainerThreadFactory(process.pid()));
            Future<byte[]> stdout = drainers.submit(() -> readAll(process.getInputStream()));
            Future<byte[]> stderr = drainers.submit(() -> readAll(process.getErrorStream()));
            drainers.shutdown();

            boolean exited = process.waitFor(
                    PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                terminate(process);
                String capturedStdout = completedOutput(stdout);
                String capturedStderr = completedOutput(stderr);
                throw new IOException("forked JVM timed out after " + PROCESS_TIMEOUT
                        + System.lineSeparator() + capturedStdout
                        + System.lineSeparator() + capturedStderr);
            }

            String capturedStdout = awaitOutput(stdout, "stdout");
            String capturedStderr = awaitOutput(stderr, "stderr");
            if (!drainers.awaitTermination(
                    TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("forked JVM output drainers did not terminate");
            }
            return new ForkResult(process.exitValue(), capturedStdout, capturedStderr);
        } catch (IOException | InterruptedException | RuntimeException | Error failure) {
            primaryFailure = failure;
            restoreInterrupt = failure instanceof InterruptedException;
            throw failure;
        } finally {
            try {
                cleanup(process, drainers);
            } catch (InterruptedException cleanupFailure) {
                restoreInterrupt = true;
                if (primaryFailure == null) {
                    throw cleanupFailure;
                }
                primaryFailure.addSuppressed(cleanupFailure);
            } catch (IOException cleanupFailure) {
                if (primaryFailure == null) {
                    throw cleanupFailure;
                }
                primaryFailure.addSuppressed(cleanupFailure);
            } finally {
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    static String targetClasspath(Path agentJar) {
        Path artifact = Objects.requireNonNull(agentJar, "agentJar")
                .toAbsolutePath()
                .normalize();
        String classpath = System.getProperty("surefire.test.class.path");
        if (classpath == null || classpath.isBlank()) {
            classpath = System.getProperty("java.class.path");
        }
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("forked JVM target classpath is unavailable");
        }
        String separator = System.getProperty("path.separator");
        String filtered = java.util.Arrays.stream(
                        classpath.split(Pattern.quote(separator), -1))
                .filter(entry -> !entry.isBlank())
                .map(entry -> Path.of(entry).toAbsolutePath().normalize())
                .filter(entry -> !entry.equals(artifact))
                .filter(entry -> !isAgentImplementationEntry(entry))
                .map(Path::toString)
                .collect(java.util.stream.Collectors.joining(separator));
        if (filtered.isBlank()) {
            throw new IllegalStateException("forked JVM target classpath is empty after filtering");
        }
        return filtered;
    }

    private static boolean isAgentImplementationEntry(Path entry) {
        Path fileName = entry.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        if (name.endsWith(".jar")
                && AGENT_ARTIFACT_PREFIXES.stream().anyMatch(name::startsWith)) {
            return true;
        }
        if (!"classes".equals(name)) {
            return false;
        }
        Path target = entry.getParent();
        Path module = target == null ? null : target.getParent();
        return target != null
                && "target".equals(String.valueOf(target.getFileName()))
                && module != null
                && AGENT_MODULES.contains(String.valueOf(module.getFileName()));
    }

    private static Path requireReadableFile(Path supplied, String parameter) throws IOException {
        Path path = Objects.requireNonNull(supplied, parameter)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException(parameter + " is not a readable file: " + path);
        }
        return path;
    }

    private static List<String> immutableStrings(List<String> values, String parameter) {
        Objects.requireNonNull(values, parameter);
        List<String> copy = List.copyOf(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(parameter + " contains null");
        }
        return copy;
    }

    private static Path javaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable)
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(java)) {
            throw new IllegalStateException("Java executable is unavailable: " + java);
        }
        return java;
    }

    private static boolean isYierLoomEnvironmentVariable(String name) {
        return name.toUpperCase(Locale.ROOT).startsWith(YIERLOOM_ENV_PREFIX);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    private static ThreadFactory drainerThreadFactory(long processId) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    task,
                    "yierloom-fork-" + processId + "-drain-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (input) {
            return input.readAllBytes();
        }
    }

    private static String awaitOutput(Future<byte[]> output, String stream)
            throws IOException, InterruptedException {
        try {
            return new String(
                    output.get(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                    StandardCharsets.UTF_8);
        } catch (ExecutionException failure) {
            throw new IOException("failed to drain forked JVM " + stream, failure.getCause());
        } catch (TimeoutException failure) {
            throw new IOException("timed out draining forked JVM " + stream, failure);
        }
    }

    private static String completedOutput(Future<byte[]> output) throws InterruptedException {
        try {
            return new String(
                    output.get(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS),
                    StandardCharsets.UTF_8);
        } catch (ExecutionException | TimeoutException ignored) {
            return "<output unavailable>";
        }
    }

    private static void terminate(Process process) throws InterruptedException, IOException {
        if (!process.isAlive()) {
            return;
        }
        process.destroyForcibly();
        if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("forked JVM could not be terminated: pid=" + process.pid());
            }
        }
    }

    private static void cleanup(Process process, ExecutorService drainers)
            throws IOException, InterruptedException {
        try {
            terminate(process);
        } finally {
            closeStreams(process);
            if (drainers != null) {
                drainers.shutdownNow();
            }
        }
    }

    private static void closeStreams(Process process) {
        close(process.getInputStream());
        close(process.getErrorStream());
        close(process.getOutputStream());
    }

    private static void close(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
