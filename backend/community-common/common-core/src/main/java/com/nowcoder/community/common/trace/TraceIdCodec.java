package com.nowcoder.community.common.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;

import java.util.Locale;
import java.util.UUID;

/**
 * traceId 编解码/规范化工具（契约侧纯工具类）：
 * - 负责 traceId 的生成、规范化、从 W3C traceparent 提取等纯逻辑
 * - 不引入 ThreadLocal/MDC/Spring Web 等运行期实现细节（避免 contracts 泄漏 runtime）
 */
public final class TraceIdCodec {

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
     * 构造 W3C traceparent：00-traceid-spanid-01
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
        String f = normalizeTraceFlags(flags);
        return "00-" + t + "-" + s + "-" + f;
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

    private static String normalizeTraceFlags(String flags) {
        if (flags == null) {
            return TraceFlags.getSampled().asHex();
        }
        String normalized = flags.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != TraceFlags.getLength()) {
            return TraceFlags.getSampled().asHex();
        }
        try {
            return TraceFlags.fromHex(normalized, 0).asHex();
        } catch (IllegalArgumentException ignored) {
            return TraceFlags.getSampled().asHex();
        }
    }
}
