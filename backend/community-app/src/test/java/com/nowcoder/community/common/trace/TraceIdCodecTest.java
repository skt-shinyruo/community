package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdCodecTest {

    private static final String TRACEPARENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void resolveTraceIdShouldUseTraceparentTraceId() {
        String resolved = TraceIdCodec.resolveTraceId(traceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).isEqualTo(TRACEPARENT_TRACE_ID);
    }

    @Test
    void resolveTraceIdShouldGenerateWhenTraceparentMissing() {
        String resolved = TraceIdCodec.resolveTraceId(null);

        assertThat(resolved).matches("[0-9a-f]{32}");
    }

    @Test
    void resolveTraceIdShouldGenerateWhenTraceparentIsInvalid() {
        String resolved = TraceIdCodec.resolveTraceId(invalidVersionTraceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).matches("[0-9a-f]{32}");
    }

    @Test
    void resolveTraceIdShouldGenerateWhenTraceparentSpanIdAllZeros() {
        String resolved = TraceIdCodec.resolveTraceId(zeroSpanTraceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void extractSpanIdFromTraceparentShouldReturnValidSpanId() {
        String spanId = TraceIdCodec.extractSpanIdFromTraceparent(traceparent(TRACEPARENT_TRACE_ID));

        assertThat(spanId).isEqualTo("00f067aa0ba902b7");
    }

    @Test
    void buildTraceparentShouldUseProvidedSpanIdAndFlags() {
        String traceparent = TraceIdCodec.buildTraceparent(
                TRACEPARENT_TRACE_ID,
                "00f067aa0ba902b7",
                "00"
        );

        assertThat(traceparent).isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00");
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
    }

    private static String invalidVersionTraceparent(String traceId) {
        return "ff-" + traceId + "-00f067aa0ba902b7-01";
    }

    private static String zeroSpanTraceparent(String traceId) {
        return "00-" + traceId + "-0000000000000000-01";
    }
}
