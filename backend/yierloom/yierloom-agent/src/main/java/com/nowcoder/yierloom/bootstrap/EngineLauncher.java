package com.nowcoder.yierloom.bootstrap;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

final class EngineLauncher {
    private static final String ENGINE_CLASS = "com.nowcoder.yierloom.core.YierLoomEngine";

    private EngineLauncher() {
    }

    static void launch(String agentArguments, Instrumentation instrumentation) throws Throwable {
        Objects.requireNonNull(instrumentation, "instrumentation");
        NestedJarExtractor.Extraction extraction = null;
        BootstrapResources resources = null;
        BootstrapResourceLease lease = null;
        try {
            extraction = NestedJarExtractor.extractOwned(NestedJarExtractor.locateAgentJar());
            resources = BootstrapResources.create(extraction);
            lease = new BootstrapResourceLease(resources);
            resources.open(instrumentation);
            BootstrapResources launchedResources = resources;
            BootstrapResourceLease launchedLease = lease;
            Throwable engineFailure = BootstrapFailures.capture(() -> {
                try {
                    invokeEngine(
                            agentArguments,
                            instrumentation,
                            launchedResources,
                            launchedLease);
                } catch (InvocationTargetException invocationFailure) {
                    Throwable cause = invocationFailure.getCause();
                    throwUnchecked(cause == null ? invocationFailure : cause);
                } catch (Throwable failure) {
                    throwUnchecked(failure);
                }
            });
            if (engineFailure != null && lease.ownershipAccepted()) {
                lease.run();
            }
            Throwable completionFailure = lease.ownershipAccepted()
                    ? BootstrapFailures.capture(lease::completeLaunch)
                    : null;
            Throwable selected = BootstrapFailures.preferred(engineFailure, completionFailure);
            throwUnchecked(selected);
            if (!lease.ownershipAccepted()) {
                throw new IllegalStateException("YierLoom Core did not accept bootstrap resources");
            }
        } catch (Throwable failure) {
            NestedJarExtractor.Extraction failedExtraction = extraction;
            Throwable cleanupFailure = lease != null && lease.ownershipAccepted()
                    ? null
                    : resources == null
                            ? BootstrapFailures.capture(
                                    () -> NestedJarExtractor.deletePrivateDirectory(failedExtraction))
                            : BootstrapFailures.capture(
                                    lease == null ? resources : lease::cleanupNow);
            throw BootstrapFailures.preferred(failure, cleanupFailure);
        }
    }

    private static void invokeEngine(
            String agentArguments,
            Instrumentation instrumentation,
            BootstrapResources resources,
            BootstrapResourceLease lease
    ) throws Throwable {
        ClassLoader engineLoader = resources.engineLoader();
        Class<?> engine = Class.forName(ENGINE_CLASS, true, engineLoader);
        if (engine.getClassLoader() != engineLoader) {
            throw new IllegalStateException("YierLoom Core is not Engine ClassLoader-owned");
        }
        Method start = engine.getMethod(
                "start", String.class, Instrumentation.class, Path.class, Runnable.class);
        start.invoke(
                null,
                agentArguments,
                instrumentation,
                resources.extractionDirectory(),
                lease);
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("YierLoom Engine invocation failed", failure);
    }
}
