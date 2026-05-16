package com.nowcoder.community.common.trace;

import org.slf4j.MDC;

/**
 * traceId 的线程上下文工具：统一维护 ThreadLocal TraceId 与 MDC（用于日志串联）。
 *
 * <p>说明：HTTP 链路通常由 TraceIdFilter 注入；异步/消息消费等非 HTTP 场景可显式调用 set/clear。</p>
 */
public final class TraceContext {

    public static final String MDC_KEY_TRACE_ID = "trace.id";
    public static final String MDC_KEY_SPAN_ID = "span.id";
    public static final String MDC_KEY_LEGACY_TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static void set(String traceId) {
        set(traceId, null);
    }

    public static void set(String traceId, String spanId) {
        if (traceId == null || traceId.isBlank()) {
            return;
        }
        String t = traceId.trim();
        if (t.isEmpty()) {
            return;
        }
        TraceId.set(t);
        MDC.put(MDC_KEY_TRACE_ID, t);
        MDC.put(MDC_KEY_LEGACY_TRACE_ID, t);
        String normalizedSpanId = TraceIdCodec.normalizeSpanId(spanId);
        if (normalizedSpanId == null) {
            MDC.remove(MDC_KEY_SPAN_ID);
            return;
        }
        MDC.put(MDC_KEY_SPAN_ID, normalizedSpanId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY_TRACE_ID);
        MDC.remove(MDC_KEY_SPAN_ID);
        MDC.remove(MDC_KEY_LEGACY_TRACE_ID);
        TraceId.clear();
    }
}
