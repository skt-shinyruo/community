package com.nowcoder.yierloom.core.instrumentation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformationErrorReporterTest {

    @Test
    void logsFirstFailureThenOneSanitizedAggregateAfterSixtySeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-29T10:00:00Z"));
        List<String> reports = new ArrayList<>();
        TransformationErrorReporter reporter = new TransformationErrorReporter(clock, reports::add);

        reporter.report("alpha", "http", "transformer", "example.First",
                new IllegalStateException("token=private-first"));
        reporter.report("alpha", "http", "transformer", "example.Second",
                new IllegalArgumentException("token=private-second"));
        clock.advance(Duration.ofSeconds(60));
        reporter.report("alpha", "http", "transformer", "example.Third",
                new IllegalStateException("token=private-third"));

        assertThat(reports).hasSize(2);
        assertThat(reports.get(0)).contains(
                "plugin=alpha", "module=http", "stage=transformer", "target=example.First");
        assertThat(reports.get(1)).contains(
                "plugin=alpha", "module=http", "stage=transformer", "count=2");
        assertThat(reports).allSatisfy(report -> assertThat(report)
                .doesNotContain("private-first", "private-second", "private-third"));
    }

    @Test
    void ratesAreIndependentByPluginModuleAndStage() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        List<String> reports = new ArrayList<>();
        TransformationErrorReporter reporter = new TransformationErrorReporter(clock, reports::add);
        RuntimeException failure = new RuntimeException("private");

        reporter.report("alpha", "one", "matcher", "example.Target", failure);
        reporter.report("alpha", "one", "transformer", "example.Target", failure);
        reporter.report("alpha", "two", "matcher", "example.Target", failure);
        reporter.report("beta", "one", "matcher", "example.Target", failure);

        assertThat(reports).hasSize(4);
    }

    @Test
    void reportingSinkFailureIsIsolatedButVmFatalFailureIsRethrown() {
        TransformationErrorReporter isolated = new TransformationErrorReporter(
                Clock.systemUTC(), ignored -> { throw new IllegalStateException("sink failed"); });

        isolated.report("alpha", "one", "transformer", "example.Target",
                new IllegalArgumentException("original"));

        TransformationErrorReporter reporter = new TransformationErrorReporter(
                Clock.systemUTC(), ignored -> { });
        assertThatThrownBy(() -> reporter.report(
                "alpha", "one", "transformer", "example.Target", new OutOfMemoryError("fatal")))
                .isInstanceOf(OutOfMemoryError.class);

        RuntimeException wrapped = new RuntimeException("ordinary wrapper");
        wrapped.addSuppressed(new OutOfMemoryError("suppressed fatal"));
        assertThatThrownBy(() -> reporter.report(
                "alpha", "one", "transformer", "example.Target", wrapped))
                .isInstanceOf(OutOfMemoryError.class);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
