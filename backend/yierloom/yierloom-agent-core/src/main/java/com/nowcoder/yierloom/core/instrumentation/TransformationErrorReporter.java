package com.nowcoder.yierloom.core.instrumentation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

final class TransformationErrorReporter {
    private static final Duration SUMMARY_INTERVAL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Consumer<String> reportSink;
    private final ConcurrentMap<FailureKey, FailureState> failures = new ConcurrentHashMap<>();

    TransformationErrorReporter(Clock clock, Consumer<String> reportSink) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reportSink = Objects.requireNonNull(reportSink, "reportSink");
    }

    void report(
            String pluginId,
            String moduleId,
            String stage,
            String targetClass,
            Throwable failure
    ) {
        Objects.requireNonNull(failure, "failure");
        PluginInstrumentationException.rethrowIfFatal(failure);
        try {
            FailureKey key = new FailureKey(pluginId, moduleId, stage);
            failures.computeIfAbsent(key, ignored -> new FailureState())
                    .record(key, targetClass, failure.getClass().getName(), clock.instant(), reportSink);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable reportingFailure) {
            PluginInstrumentationException.rethrowIfFatal(reportingFailure);
            // Diagnostics must not replace the transformation failure being reported.
        }
    }

    private record FailureKey(String pluginId, String moduleId, String stage) {
        private FailureKey {
            pluginId = Objects.requireNonNull(pluginId, "pluginId");
            moduleId = Objects.requireNonNull(moduleId, "moduleId");
            stage = Objects.requireNonNull(stage, "stage");
        }
    }

    private static final class FailureState {
        private Instant lastReport;
        private long unreportedFailures;

        private synchronized void record(
                FailureKey key,
                String targetClass,
                String failureType,
                Instant now,
                Consumer<String> sink
        ) {
            if (lastReport == null) {
                safeAccept(sink, "YierLoom transformation failure [plugin=" + key.pluginId()
                        + ", module=" + key.moduleId()
                        + ", stage=" + key.stage()
                        + ", target=" + Objects.requireNonNull(targetClass, "targetClass")
                        + ", failure=" + failureType + "]");
                lastReport = now;
                return;
            }
            unreportedFailures++;
            if (!now.isBefore(lastReport.plus(SUMMARY_INTERVAL))) {
                safeAccept(sink, "YierLoom transformation failures [plugin=" + key.pluginId()
                        + ", module=" + key.moduleId()
                        + ", stage=" + key.stage()
                        + ", count=" + unreportedFailures + "]");
                lastReport = now;
                unreportedFailures = 0;
            }
        }

        private static void safeAccept(Consumer<String> sink, String message) {
            try {
                sink.accept(message);
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (Throwable failure) {
                PluginInstrumentationException.rethrowIfFatal(failure);
                // Logging infrastructure is outside the transformation path's trust boundary.
            }
        }
    }
}
