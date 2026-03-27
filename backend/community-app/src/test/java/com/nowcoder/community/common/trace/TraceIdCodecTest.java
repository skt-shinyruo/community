package com.nowcoder.community.common.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdCodecTest {

    private static final String TRACEPARENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String LEGACY_TRACE_ID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String UPPERCASE_LEGACY_TRACE_ID = "ABCDEFABCDEFABCDEFABCDEFABCDEFAB";

    @Test
    void resolveTraceIdShouldPreferTraceparentOverLegacyHeader() {
        String resolved = TraceIdCodec.resolveTraceId(LEGACY_TRACE_ID, traceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).isEqualTo(TRACEPARENT_TRACE_ID);
    }

    @Test
    void resolveTraceIdShouldFallbackToNormalizedLegacyHeaderWhenTraceparentMissing() {
        String resolved = TraceIdCodec.resolveTraceId(UPPERCASE_LEGACY_TRACE_ID, null);

        assertThat(resolved).isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
    }

    @Test
    void resolveTraceIdShouldFallbackToLegacyHeaderWhenTraceparentVersionInvalid() {
        String resolved = TraceIdCodec.resolveTraceId(UPPERCASE_LEGACY_TRACE_ID, invalidVersionTraceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).isEqualTo(UPPERCASE_LEGACY_TRACE_ID.toLowerCase());
    }

    @Test
    void resolveTraceIdShouldGenerateWhenTraceparentSpanIdAllZerosAndLegacyMissing() {
        String resolved = TraceIdCodec.resolveTraceId(null, zeroSpanTraceparent(TRACEPARENT_TRACE_ID));

        assertThat(resolved).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void resolveTraceIdShouldReplaceInvalidLegacyHeaderWithGeneratedTraceId() {
        String resolved = TraceIdCodec.resolveTraceId("trace-123", null);

        assertThat(resolved).isNotBlank().hasSize(32).matches("[0-9a-f]{32}");
        assertThat(resolved).isNotEqualTo("trace-123");
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
