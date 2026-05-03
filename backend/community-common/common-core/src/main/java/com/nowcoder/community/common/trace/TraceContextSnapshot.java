package com.nowcoder.community.common.trace;

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

    public static TraceContextSnapshot fromInbound(String traceIdHeader, String traceparentHeader) {
        String resolved = TraceIdCodec.resolveTraceId(traceIdHeader, traceparentHeader);
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparentHeader);
        String parent = resolved.equals(extracted) ? traceparentHeader : TraceIdCodec.buildTraceparent(resolved);
        return new TraceContextSnapshot(resolved, parent, false);
    }

    public static TraceContextSnapshot fromStored(String traceId, String traceparent) {
        String normalized = TraceIdCodec.normalizeTraceId(traceId);
        boolean recovered = normalized == null;
        return new TraceContextSnapshot(normalized, traceparent, recovered);
    }

    public static TraceContextSnapshot currentOrNew() {
        String current = TraceIdCodec.normalizeTraceId(TraceId.get());
        boolean recovered = current == null;
        return new TraceContextSnapshot(current, null, recovered);
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
