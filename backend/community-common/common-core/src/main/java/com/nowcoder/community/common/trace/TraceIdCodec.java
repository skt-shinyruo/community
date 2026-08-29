package com.nowcoder.community.common.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * traceId 编解码/规范化工具（契约侧纯工具类）：
 * - 负责 traceId 的生成、规范化、W3C traceparent 的提取与构造等纯逻辑
 * - W3C traceparent 的解析与序列化全部委托给 OTel 的 {@link W3CTraceContextPropagator}
 * - 不引入 ThreadLocal/MDC/Spring Web 等运行期实现细节（避免 contracts 泄漏 runtime）
 */
public final class TraceIdCodec {

    private static final TextMapSetter<Map<String, String>> TRACEPARENT_SETTER = (carrier, key, value) -> {
        if (carrier != null && key != null && value != null) {
            carrier.put(key, value);
        }
    };

    private TraceIdCodec() {
    }

    /**
     * 生成 32 位小写 hex traceId（UUID 去横杠）。
     */
    public static String generateTraceId() {
        UUID uuid = UUID.randomUUID();
        return io.opentelemetry.api.trace.TraceId.fromLongs(
                uuid.getMostSignificantBits(),
                uuid.getLeastSignificantBits()
        );
    }

    /**
     * 规范化 traceId：
     * - 必须为 32 位 hex
     * - 统一输出为小写
     *
     * @return 合法则返回规范化后的值；非法返回 null
     */
    public static String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        String normalized = traceId.trim().toLowerCase(Locale.ROOT);
        return io.opentelemetry.api.trace.TraceId.isValid(normalized) ? normalized : null;
    }

    public static String normalizeSpanId(String spanId) {
        if (spanId == null || spanId.isBlank()) {
            return null;
        }
        String normalized = spanId.trim().toLowerCase(Locale.ROOT);
        return SpanId.isValid(normalized) ? normalized : null;
    }

    /**
     * 从 W3C Trace Context 的 traceparent 中提取 traceId。
     * 格式：version-traceid-spanid-flags，例如：
     * 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
     */
    public static String extractTraceIdFromTraceparent(String traceparent) {
        SpanContext spanContext = extractSpanContext(traceparent);
        return spanContext.isValid() ? spanContext.getTraceId() : null;
    }

    public static String extractSpanIdFromTraceparent(String traceparent) {
        SpanContext spanContext = extractSpanContext(traceparent);
        return spanContext.isValid() ? spanContext.getSpanId() : null;
    }

    /**
     * 解析请求侧 traceId：使用合法 traceparent，缺失/非法则生成新的 traceId。
     */
    public static String resolveTraceId(String traceparentHeader) {
        String traceId = extractTraceIdFromTraceparent(traceparentHeader);
        return traceId == null ? generateTraceId() : traceId;
    }

    /**
     * 构造 W3C traceparent，序列化由 OTel W3C propagator 完成。
     */
    public static String buildTraceparent(String traceId) {
        return buildTraceparent(traceId, null, "01");
    }

    public static String buildTraceparent(String traceId, String spanId, String flags) {
        String t = normalizeTraceId(traceId);
        if (t == null) {
            t = generateTraceId();
        }
        String s = normalizeSpanId(spanId);
        if (s == null) {
            s = generateSpanId();
        }
        SpanContext context = SpanContext.create(t, s, normalizeTraceFlags(flags), TraceState.getDefault());
        Map<String, String> carrier = new HashMap<>();
        W3CTraceContextPropagator.getInstance()
                .inject(Context.root().with(Span.wrap(context)), carrier, TRACEPARENT_SETTER);
        return carrier.get(TraceHeaders.HEADER_TRACEPARENT);
    }

    private static String generateSpanId() {
        String spanId;
        do {
            spanId = SpanId.fromLong(UUID.randomUUID().getLeastSignificantBits());
        } while (!SpanId.isValid(spanId));
        return spanId;
    }

    private static SpanContext extractSpanContext(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return SpanContext.getInvalid();
        }
        return Span.fromContext(OtelTraceContext.extract(
                traceparent.trim().toLowerCase(Locale.ROOT)
        )).getSpanContext();
    }

    private static TraceFlags normalizeTraceFlags(String flags) {
        if (flags == null) {
            return TraceFlags.getSampled();
        }
        String normalized = flags.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != TraceFlags.getLength()) {
            return TraceFlags.getSampled();
        }
        try {
            return TraceFlags.fromHex(normalized, 0);
        } catch (IllegalArgumentException ignored) {
            return TraceFlags.getSampled();
        }
    }
}
