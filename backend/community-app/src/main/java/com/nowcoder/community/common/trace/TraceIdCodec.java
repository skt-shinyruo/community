package com.nowcoder.community.common.trace;

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
        return UUID.randomUUID().toString().replace("-", "");
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
        String t = traceId.trim();
        if (t.length() != 32) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return null;
            }
        }
        return t.toLowerCase();
    }

    /**
     * 从 W3C Trace Context 的 traceparent 中提取 traceId。
     * 格式：version-traceid-spanid-flags，例如：
     * 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
     */
    public static String extractTraceIdFromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        return normalizeTraceId(parts[1]);
    }

    /**
     * 解析请求侧 traceId：
     * - 优先使用 X-Trace-Id（若合法）
     * - 否则尝试从 traceparent 提取（若合法）
     * - 都缺失/非法则生成新的 traceId
     */
    public static String resolveTraceId(String traceIdHeader, String traceparentHeader) {
        String traceId = normalizeTraceId(traceIdHeader);
        if (traceId == null) {
            traceId = extractTraceIdFromTraceparent(traceparentHeader);
        }
        return traceId == null ? generateTraceId() : traceId;
    }

    /**
     * 构造 W3C traceparent：00-traceid-spanid-01
     */
    public static String buildTraceparent(String traceId) {
        String t = normalizeTraceId(traceId);
        if (t == null) {
            t = generateTraceId();
        }
        String spanId = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
        spanId = spanId.replace("-", "");
        if (spanId.length() < 16) {
            spanId = "0".repeat(16 - spanId.length()) + spanId;
        } else if (spanId.length() > 16) {
            spanId = spanId.substring(spanId.length() - 16);
        }
        spanId = spanId.toLowerCase();
        return "00-" + t + "-" + spanId + "-01";
    }
}

