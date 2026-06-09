package com.nowcoder.observability.runtimediagnostics;

import com.nowcoder.observability.runtimediagnostics.core.DiagnosticRuntime;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDiagnosticsAgentSmokeTest {

    @Test
    void exposesPremainEntryPoint() throws Exception {
        assertThat(RuntimeDiagnosticsAgent.class.getMethod("premain", String.class, Instrumentation.class))
                .isNotNull();
    }

    @Test
    void startupFailureDoesNotLeaveScheduledProbesRunning() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);

            RuntimeDiagnosticsAgent.premain(
                    "enabled=true,probes=method,thread,jvm,includes=com.example.*,summaryInterval=1s,threadSnapshotInterval=1s,jvmSummaryInterval=1s",
                    new FailingInstrumentation()
            );
            Thread.sleep(1_300);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            DiagnosticRuntime.resetForTests();
        }

        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("[runtime-diagnostics-agent] disabled after startup failure:");
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .doesNotContain("\"event.action\":\"thread_snapshot\"")
                .doesNotContain("\"event.action\":\"jvm_runtime_summary\"");
    }

    @Test
    void lateStartupFailureStopsStartedDiagnostics() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        RecordingInstrumentation instrumentation = new RecordingInstrumentation();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        try (PrintStream capturedOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream capturedErr = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            System.setOut(capturedOut);
            System.setErr(capturedErr);
            RuntimeDiagnosticsAgent.replaceStartupHooksForTests(new RuntimeDiagnosticsAgent.StartupHooks() {
                @Override
                void afterProbesStarted() {
                    throw new IllegalStateException("late startup failure");
                }
            });

            RuntimeDiagnosticsAgent.premain(
                    "enabled=true,probes=method,exception,thread,jvm,includes=com.example.*,methodSlowThresholdMs=1,summaryInterval=1s,threadSnapshotInterval=1s,jvmSummaryInterval=1s",
                    instrumentation
            );
            DiagnosticRuntime.recordException(
                    "com.example.Target",
                    "work",
                    "()V",
                    new IllegalStateException("password=secret")
            );
            Thread.sleep(1_300);
        } finally {
            RuntimeDiagnosticsAgent.replaceStartupHooksForTests(null);
            System.setOut(originalOut);
            System.setErr(originalErr);
            DiagnosticRuntime.resetForTests();
        }

        assertThat(stderr.toString(StandardCharsets.UTF_8))
                .contains("[runtime-diagnostics-agent] disabled after startup failure:");
        assertThat(stdout.toString(StandardCharsets.UTF_8))
                .doesNotContain("exception_observed")
                .doesNotContain("method_latency_summary")
                .doesNotContain("thread_snapshot")
                .doesNotContain("jvm_runtime_summary")
                .doesNotContain("password=secret");
        assertThat(instrumentation.transformerRemoved()).isTrue();
    }

    private static class FailingInstrumentation implements Instrumentation {

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
            throw new IllegalStateException("transformer install disabled for test");
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
            throw new IllegalStateException("transformer install disabled for test");
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            return false;
        }

        @Override
        public boolean isRetransformClassesSupported() {
            return false;
        }

        @Override
        public void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isRedefineClassesSupported() {
            return false;
        }

        @Override
        public void redefineClasses(ClassDefinition... definitions)
                throws ClassNotFoundException, UnmodifiableClassException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isModifiableClass(Class<?> theClass) {
            return false;
        }

        @Override
        public Class[] getAllLoadedClasses() {
            return new Class[0];
        }

        @Override
        public Class[] getInitiatedClasses(ClassLoader loader) {
            return new Class[0];
        }

        @Override
        public long getObjectSize(Object objectToSize) {
            return 0;
        }

        @Override
        public void appendToBootstrapClassLoaderSearch(JarFile jarfile) {
        }

        @Override
        public void appendToSystemClassLoaderSearch(JarFile jarfile) {
        }

        @Override
        public boolean isNativeMethodPrefixSupported() {
            return false;
        }

        @Override
        public void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix) {
        }

        @Override
        public void redefineModule(
                Module module,
                Set<Module> extraReads,
                Map<String, Set<Module>> extraExports,
                Map<String, Set<Module>> extraOpens,
                Set<Class<?>> extraUses,
                Map<Class<?>, List<Class<?>>> extraProvides
        ) {
        }

        @Override
        public boolean isModifiableModule(Module module) {
            return false;
        }
    }

    private static final class RecordingInstrumentation extends FailingInstrumentation {
        private boolean transformerRemoved;

        @Override
        public void addTransformer(ClassFileTransformer transformer, boolean canRetransform) {
        }

        @Override
        public void addTransformer(ClassFileTransformer transformer) {
        }

        @Override
        public boolean removeTransformer(ClassFileTransformer transformer) {
            transformerRemoved = true;
            return true;
        }

        boolean transformerRemoved() {
            return transformerRemoved;
        }
    }
}
