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
    void fromInboundShouldPreferTraceparentAndNormalizeLegacyFallback() {
        TraceContextSnapshot fromTraceparent = TraceContextSnapshot.fromInbound(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        );

        assertThat(fromTraceparent.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(fromTraceparent.traceparent()).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(fromTraceparent.recovered()).isFalse();

        TraceContextSnapshot fromLegacy = TraceContextSnapshot.fromInbound(
                "ABCDEFABCDEFABCDEFABCDEFABCDEFAB",
                null
        );

        assertThat(fromLegacy.traceId()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(fromLegacy.traceparent()).startsWith("00-abcdefabcdefabcdefabcdefabcdefab-");
        assertThat(fromLegacy.recovered()).isFalse();
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
        TraceId.set("legacy-thread-trace");
        MDC.put(TraceContext.MDC_KEY_TRACE_ID, "legacy-mdc-trace");
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "22222222222222222222222222222222",
                "00-22222222222222222222222222222222-00f067aa0ba902b7-01"
        );

        try (TraceContextScope ignored = snapshot.open()) {
            assertThat(TraceId.get()).isEqualTo("22222222222222222222222222222222");
            assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo("22222222222222222222222222222222");
        }

        assertThat(TraceId.get()).isEqualTo("legacy-thread-trace");
        assertThat(MDC.get(TraceContext.MDC_KEY_TRACE_ID)).isEqualTo("legacy-mdc-trace");
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
