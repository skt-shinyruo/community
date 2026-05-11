package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextSnapshotTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void fromInboundShouldUseTraceparentAndGenerateWhenMissing() {
        TraceContextSnapshot fromTraceparent = TraceContextSnapshot.fromInbound(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        assertThat(fromTraceparent.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(fromTraceparent.traceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(fromTraceparent.recovered()).isFalse();

        TraceContextSnapshot generated = TraceContextSnapshot.fromInbound(null);

        assertThat(generated.traceId()).matches("[0-9a-f]{32}");
        assertThat(generated.traceparent()).startsWith("00-" + generated.traceId() + "-");
        assertThat(generated.recovered()).isTrue();
    }

    @Test
    void currentOrNewShouldCaptureCurrentTraceAndGenerateWhenMissing() {
        TraceContext.set("11111111111111111111111111111111");

        TraceContextSnapshot current = TraceContextSnapshot.currentOrNew();

        assertThat(current.traceId()).isEqualTo("11111111111111111111111111111111");
        assertThat(current.traceparent()).startsWith("00-11111111111111111111111111111111-");
        assertThat(current.recovered()).isFalse();

        TraceContext.clear();

        TraceContextSnapshot generated = TraceContextSnapshot.currentOrNew();

        assertThat(generated.traceId()).matches("[0-9a-f]{32}");
        assertThat(generated.traceparent()).startsWith("00-" + generated.traceId() + "-");
        assertThat(generated.recovered()).isTrue();
    }

    @Test
    void fromStoredShouldRecoverFromTraceparentWhenTraceIdIsMissingOrInvalid() {
        String traceparent = "00-44444444444444444444444444444444-00f067aa0ba902b7-01";

        TraceContextSnapshot missingTraceId = TraceContextSnapshot.fromStored(null, traceparent);
        TraceContextSnapshot invalidTraceId = TraceContextSnapshot.fromStored("not-a-trace-id", traceparent);

        assertThat(missingTraceId.traceId()).isEqualTo("44444444444444444444444444444444");
        assertThat(missingTraceId.traceparent()).isEqualTo(traceparent);
        assertThat(missingTraceId.recovered()).isFalse();
        assertThat(invalidTraceId.traceId()).isEqualTo("44444444444444444444444444444444");
        assertThat(invalidTraceId.traceparent()).isEqualTo(traceparent);
        assertThat(invalidTraceId.recovered()).isFalse();
    }

    @Test
    void fromStoredShouldGenerateRecoveredSnapshotWhenStoredTraceContextIsInvalid() {
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored("not-a-trace-id", "invalid-traceparent");

        assertThat(snapshot.traceId()).matches("[0-9a-f]{32}");
        assertThat(snapshot.traceparent()).startsWith("00-" + snapshot.traceId() + "-");
        assertThat(snapshot.recovered()).isTrue();
    }

    @Test
    void openShouldRestorePreviousTraceAndMdc() {
        TraceContext.set("11111111111111111111111111111111");
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "22222222222222222222222222222222",
                "00-22222222222222222222222222222222-00f067aa0ba902b7-01"
        );

        try (TraceContextScope ignored = snapshot.open()) {
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            assertThat(MDC.get("traceId")).isEqualTo("22222222222222222222222222222222");
        }

        assertThat(TraceId.get()).isEqualTo("11111111111111111111111111111111");
        assertThat(MDC.get("traceId")).isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void openShouldRestoreDivergedPreviousTraceAndMdcExactly() {
        TraceId.set("previous-thread-trace");
        MDC.put(TraceContext.MDC_KEY_TRACE_ID, "previous-mdc-trace");
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "22222222222222222222222222222222",
                "00-22222222222222222222222222222222-00f067aa0ba902b7-01"
        );

        try (TraceContextScope ignored = snapshot.open()) {
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo("22222222222222222222222222222222");
        }

        assertThat(TraceId.get()).isEqualTo("previous-thread-trace");
        assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo("previous-mdc-trace");
    }

    @Test
    void wrapShouldRunWithSnapshotAndClearGeneratedTraceAfterwards() {
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "33333333333333333333333333333333",
                null
        );
        AtomicReference<String> seen = new AtomicReference<>();

        snapshot.wrap(() -> seen.set(TraceId.get())).run();

        assertThat(seen.get()).isEqualTo("33333333333333333333333333333333");
        assertThat(TraceId.get()).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }
}
