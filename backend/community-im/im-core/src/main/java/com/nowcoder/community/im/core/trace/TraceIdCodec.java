package com.nowcoder.community.im.core.trace;

import java.util.UUID;

public final class TraceIdCodec {

    private TraceIdCodec() {
    }

    /**
     * 生成 32 位小写 hex traceId（UUID 去横杠）。
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

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

