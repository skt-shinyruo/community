package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceJobRunnerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void runShouldCreateTraceWhenJobHasNoUpstreamContextAndClearAfterwards() {
        AtomicReference<String> seen = new AtomicReference<>();

        TraceJobRunner.run("test-job", () -> seen.set(TraceId.get()));

        assertThat(seen.get()).matches("[0-9a-f]{32}");
        assertThat(TraceId.get()).isNull();
    }

    @Test
    void runShouldCreateInternalSpanAndRestoreLegacyTraceAfterwards() {
        TraceContext.set("99999999999999999999999999999999");
        AtomicReference<String> seen = new AtomicReference<>();

        TraceJobRunner.run("test-job", () -> seen.set(TraceId.get()));

        assertThat(seen.get()).matches("[0-9a-f]{32}");
        assertThat(seen.get()).isNotEqualTo("99999999999999999999999999999999");
        assertThat(TraceId.get()).isEqualTo("99999999999999999999999999999999");
    }

    @Test
    void runShouldIgnoreNullAction() {
        TraceJobRunner.run("test-job", null);

        assertThat(TraceId.get()).isNull();
    }
}
