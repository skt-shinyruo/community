package com.nowcoder.yierloom.core.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import com.nowcoder.yierloom.core.FatalFailures;

final class ExporterFailureReporter {
    private static final Duration SUMMARY_INTERVAL = Duration.ofSeconds(60);

    private final Clock clock;
    private final Consumer<String> reportSink;
    private final ConcurrentMap<FailureKey, FailureState> failures = new ConcurrentHashMap<>();

    ExporterFailureReporter(Clock clock, Consumer<String> reportSink) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reportSink = Objects.requireNonNull(reportSink, "reportSink");
    }

    void report(String exporter, String stage, Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        FatalFailures.rethrow(failure);

        FailureKey key = new FailureKey(
                Objects.requireNonNull(exporter, "exporter"),
                Objects.requireNonNull(stage, "stage"));
        failures.computeIfAbsent(key, ignored -> new FailureState()).record(key, clock.instant(), reportSink);
    }

    private record FailureKey(String exporter, String stage) {
    }

    private static final class FailureState {
        private Instant lastReport;
        private long unreportedFailures;

        private synchronized void record(FailureKey key, Instant now, Consumer<String> reportSink) {
            if (lastReport == null) {
                lastReport = now;
                reportSink.accept("YierLoom exporter failure [exporter=" + key.exporter()
                        + ", stage=" + key.stage() + "]");
                return;
            }

            unreportedFailures++;
            if (!now.isBefore(lastReport.plus(SUMMARY_INTERVAL))) {
                reportSink.accept("YierLoom exporter failures [exporter=" + key.exporter()
                        + ", stage=" + key.stage() + ", count=" + unreportedFailures + "]");
                lastReport = now;
                unreportedFailures = 0;
            }
        }
    }
}
