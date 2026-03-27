package com.nowcoder.community.im.core.trace;

import org.slf4j.MDC;

/**
 * traceId thread context shared by servlet filters and logback MDC correlation.
 */
public final class TraceContext {

    public static final String MDC_KEY_TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        String normalized = traceId.trim();
        if (normalized.isEmpty()) {
            return;
        }
        TraceId.set(normalized);
        MDC.put(MDC_KEY_TRACE_ID, normalized);
    }

    public static void clear() {
        MDC.remove(MDC_KEY_TRACE_ID);
        TraceId.clear();
    }
}
