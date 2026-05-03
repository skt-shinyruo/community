package com.nowcoder.community.common.trace;

import org.slf4j.MDC;

/**
 * Restores the previous thread trace context after a boundary operation.
 */
public final class TraceContextScope implements AutoCloseable {

    private final String previousTraceId;
    private final String previousMdcTraceId;
    private boolean closed;

    private TraceContextScope(String previousTraceId, String previousMdcTraceId) {
        this.previousTraceId = previousTraceId;
        this.previousMdcTraceId = previousMdcTraceId;
    }

    static TraceContextScope open(TraceContextSnapshot snapshot) {
        TraceContextScope scope = new TraceContextScope(
                TraceId.get(),
                MDC.get(TraceContext.MDC_KEY_TRACE_ID)
        );
        if (snapshot != null) {
            TraceContext.set(snapshot.traceId());
        }
        return scope;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        TraceContext.clear();
        if (previousTraceId != null) {
            TraceId.set(previousTraceId);
        }
        if (previousMdcTraceId != null) {
            MDC.put(TraceContext.MDC_KEY_TRACE_ID, previousMdcTraceId);
        }
    }
}
