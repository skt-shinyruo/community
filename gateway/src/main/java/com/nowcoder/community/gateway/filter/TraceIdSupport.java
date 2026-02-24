package com.nowcoder.community.gateway.filter;

// 网关 traceId 解析与规范化工具：统一处理 X-Trace-Id/traceparent 并生成规范化结果。
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.http.HttpHeaders;

public final class TraceIdSupport {

    public static final String HEADER_TRACE_ID = TraceHeaders.HEADER_TRACE_ID;
    public static final String HEADER_TRACEPARENT = TraceHeaders.HEADER_TRACEPARENT;

    private TraceIdSupport() {
    }

    public static String resolveTraceId(HttpHeaders headers) {
        if (headers == null) {
            return TraceIdCodec.generateTraceId();
        }
        return TraceIdCodec.resolveTraceId(
                headers.getFirst(HEADER_TRACE_ID),
                headers.getFirst(HEADER_TRACEPARENT)
        );
    }

    public static String normalizeTraceId(String traceId) {
        return TraceIdCodec.normalizeTraceId(traceId);
    }

    public static String extractTraceIdFromTraceparent(String traceparent) {
        return TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
    }

    public static String buildTraceparent(String traceId) {
        return TraceIdCodec.buildTraceparent(traceId);
    }

    public static String generateTraceId() {
        return TraceIdCodec.generateTraceId();
    }
}
