package com.nowcoder.community.common.trace;

import io.opentelemetry.api.trace.SpanContext;

/**
 * Immutable trace context captured at a technical boundary.
 */
public record TraceContextSnapshot(String traceId, String traceparent, boolean recovered) {

    public TraceContextSnapshot {
        traceId = TraceIdCodec.normalizeTraceId(traceId);
        if (traceId == null) {
            traceId = TraceIdCodec.generateTraceId();
            recovered = true;
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
        String resolved = TraceIdCodec.resolveTraceId(traceparentHeader);
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparentHeader);
        String parent = resolved.equals(extracted) ? traceparentHeader : TraceIdCodec.buildTraceparent(resolved);
        return new TraceContextSnapshot(resolved, parent, extracted == null);
    }

    public static TraceContextSnapshot fromStored(String traceId, String traceparent) {
        String normalized = TraceIdCodec.normalizeTraceId(traceId);
        if (normalized == null) {
            normalized = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        }
        boolean recovered = normalized == null;
        return new TraceContextSnapshot(normalized, traceparent, recovered);
    }

    public static TraceContextSnapshot currentOrNew() {
        SpanContext spanContext = OtelTraceContext.currentSpanContext();
        if (spanContext != null) {
            return fromSpanContext(spanContext, false);
        }
        String current = TraceIdCodec.normalizeTraceId(TraceId.threadLocalValue());
        boolean recovered = current == null;
        return new TraceContextSnapshot(current, null, recovered);
    }

    public static TraceContextSnapshot fromSpanContext(SpanContext spanContext, boolean recovered) {
        if (spanContext == null || !spanContext.isValid()) {
            return synthetic();
        }
        return new TraceContextSnapshot(
                spanContext.getTraceId(),
                OtelTraceContext.traceparent(spanContext),
                recovered
        );
    }

    public static TraceContextSnapshot synthetic() {
        return new TraceContextSnapshot(TraceIdCodec.generateTraceId(), null, true);
    }

    public TraceContextScope open() {
        return TraceContextScope.open(this);
    }

    public Runnable wrap(Runnable action) {
        if (action == null) {
            return () -> {
            };
        }
        return () -> {
            try (TraceContextScope ignored = open()) {
                action.run();
            }
        };
    }
}
