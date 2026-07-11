package com.nowcoder.community.common.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

/**
 * Restores the previous thread trace context after a boundary operation.
 */
public final class TraceContextScope implements AutoCloseable {

    private final String previousTraceId;
    private final String previousMdcTraceId;
    private final String previousMdcSpanId;
    private final Scope otelScope;
    private final Span span;
    private boolean closed;

    private TraceContextScope(
            String previousTraceId,
            String previousMdcTraceId,
            String previousMdcSpanId,
            Scope otelScope,
            Span span
    ) {
        this.previousTraceId = previousTraceId;
        this.previousMdcTraceId = previousMdcTraceId;
        this.previousMdcSpanId = previousMdcSpanId;
        this.otelScope = otelScope;
        this.span = span;
    }

    static TraceContextScope open(TraceContextSnapshot snapshot) {
        return open(snapshot, Scope.noop(), null);
    }

    static TraceContextScope open(TraceContextSnapshot snapshot, Scope otelScope, Span span) {
        TraceContextScope scope = new TraceContextScope(
                TraceId.threadLocalValue(),
                MDC.get(TraceContext.MDC_KEY_TRACE_ID),
                MDC.get(TraceContext.MDC_KEY_SPAN_ID),
                otelScope,
                span
        );
        if (snapshot != null) {
            TraceContext.set(snapshot.traceId(), snapshot.spanId());
        }
        return scope;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            TraceContext.clear();
            if (previousTraceId != null) {
                TraceId.set(previousTraceId);
            }
            restore(TraceContext.MDC_KEY_TRACE_ID, previousMdcTraceId);
            restore(TraceContext.MDC_KEY_SPAN_ID, previousMdcSpanId);
        } finally {
            try {
                if (otelScope != null) {
                    otelScope.close();
                }
            } finally {
                if (span != null) {
                    span.end();
                }
            }
        }
    }

    private void restore(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, previousValue);
    }
}
