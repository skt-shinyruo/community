package com.nowcoder.yierloom.core;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import com.nowcoder.yierloom.core.config.YierLoomConfig;
import com.nowcoder.yierloom.core.config.YierLoomConfigLoader;

public final class YierLoomEngine {
    private YierLoomEngine() {
    }

    public static void start(
            String agentArguments,
            Instrumentation instrumentation,
            Path workDirectory,
            Runnable bootstrapCleanup
    ) {
        BootstrapCleanup cleanup = new BootstrapCleanup(Objects.requireNonNull(
                bootstrapCleanup, "bootstrapCleanup"));
        EngineRuntime runtime = null;
        try {
            cleanup.acceptOwnership();
            YierLoomConfig config = YierLoomConfigLoader.load(
                    agentArguments,
                    systemProperties(),
                    System.getenv(),
                    Path.of(System.getProperty("user.dir", ".")));
            if (!config.enabled()) {
                cleanup.run();
                return;
            }

            runtime = EngineRuntime.start(
                    config,
                    instrumentation,
                    workDirectory,
                    cleanup);
            EngineRuntime startedRuntime = runtime;
            cleanup.installShutdownHook(startedRuntime::close);
        } catch (Throwable failure) {
            Throwable cleanupFailure = cleanup(runtime, cleanup);
            Throwable fatal = FatalFailures.find(failure);
            if (fatal == null) {
                fatal = FatalFailures.find(cleanupFailure);
            }
            FatalFailures.rethrow(fatal);
            report("[YierLoom] disabled after engine failure: "
                    + failure.getClass().getName());
        }
    }

    private static Throwable cleanup(EngineRuntime runtime, BootstrapCleanup bootstrapCleanup) {
        Throwable first = captureCleanup(runtime, bootstrapCleanup);
        Throwable retry = bootstrapCleanup.retained()
                ? null
                : captureCleanup(runtime, bootstrapCleanup);
        Throwable fatal = FatalFailures.find(first);
        if (fatal == null) {
            fatal = FatalFailures.find(retry);
        }
        return fatal == null ? first == null ? retry : first : fatal;
    }

    private static Throwable captureCleanup(
            EngineRuntime runtime,
            BootstrapCleanup bootstrapCleanup
    ) {
        try {
            if (runtime != null) {
                runtime.close();
            } else {
                bootstrapCleanup.run();
            }
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static void report(String message) {
        try {
            System.err.println(message);
        } catch (Throwable failure) {
            FatalFailures.rethrow(failure);
        }
    }

    private static Map<String, String> systemProperties() {
        Properties properties = System.getProperties();
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String name : properties.stringPropertyNames()) {
            snapshot.put(name, properties.getProperty(name));
        }
        return Map.copyOf(snapshot);
    }
}
