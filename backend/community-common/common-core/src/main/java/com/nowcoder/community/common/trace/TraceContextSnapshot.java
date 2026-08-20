package com.nowcoder.community.common.trace;

import io.opentelemetry.api.trace.SpanContext;

/**
 * Immutable trace context captured at a technical boundary.
 */
public record TraceContextSnapshot(String traceId, String traceparent) {

    public TraceContextSnapshot {
        traceId = TraceIdCodec.normalizeTraceId(traceId);
        if (traceId == null) {
            traceId = TraceIdCodec.generateTraceId();
        }
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        if (!traceId.equals(extracted)) {
            traceparent = TraceIdCodec.buildTraceparent(traceId);
        } else {
            traceparent = traceparent.trim();
        }
    }

    public String spanId() {
        return TraceIdCodec.extractSpanIdFromTraceparent(traceparent);
    }

    public static TraceContextSnapshot fromInbound(String traceparentHeader) {
        return new TraceContextSnapshot(
                TraceIdCodec.extractTraceIdFromTraceparent(traceparentHeader),
                traceparentHeader
        );
    }

    public static TraceContextSnapshot fromStored(String traceId, String traceparent) {
        String normalized = TraceIdCodec.normalizeTraceId(traceId);
        if (normalized == null) {
            normalized = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        }
        return new TraceContextSnapshot(normalized, traceparent);
    }

    public static TraceContextSnapshot currentOrNew() {
        SpanContext spanContext = OtelTraceContext.currentSpanContext();
        if (spanContext != null) {
            return fromSpanContext(spanContext);
        }
        return new TraceContextSnapshot(TraceId.threadLocalValue(), null);
    }

    public static TraceContextSnapshot fromSpanContext(SpanContext spanContext) {
        if (spanContext == null || !spanContext.isValid()) {
            return synthetic();
        }
        return new TraceContextSnapshot(
                spanContext.getTraceId(),
                OtelTraceContext.traceparent(spanContext)
        );
    }

    public static TraceContextSnapshot synthetic() {
        return new TraceContextSnapshot(null, null);
    }
}
