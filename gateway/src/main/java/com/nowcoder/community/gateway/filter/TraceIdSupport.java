package com.nowcoder.community.gateway.filter;

// 网关 traceId 解析与规范化工具：统一处理 X-Trace-Id/traceparent 并生成规范化结果。
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class TraceIdSupport {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";

    private TraceIdSupport() {
    }

    public static String resolveTraceId(HttpHeaders headers) {
        if (headers == null) {
            return generateTraceId();
        }
        String traceId = normalizeTraceId(headers.getFirst(HEADER_TRACE_ID));
        if (traceId == null) {
            traceId = extractTraceIdFromTraceparent(headers.getFirst(HEADER_TRACEPARENT));
        }
        return traceId == null ? generateTraceId() : traceId;
    }

    public static String normalizeTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
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
        if (!StringUtils.hasText(traceparent)) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        return normalizeTraceId(parts[1]);
    }

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

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
