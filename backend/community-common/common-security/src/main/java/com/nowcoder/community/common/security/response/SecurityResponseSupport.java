package com.nowcoder.community.common.security.response;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import com.nowcoder.community.common.web.Result;

import java.util.function.BiConsumer;

public final class SecurityResponseSupport {

    private SecurityResponseSupport() {
    }

    public static Result<Void> unauthorized(String traceId, BiConsumer<String, String> headerWriter) {
        return build(Result.error(CommonErrorCode.UNAUTHORIZED), traceId, headerWriter);
    }

    public static Result<Void> forbidden(String traceId, BiConsumer<String, String> headerWriter) {
        return build(Result.error(CommonErrorCode.FORBIDDEN), traceId, headerWriter);
    }

    public static String resolveTraceId(String currentTraceId, String traceparent) {
        String normalizedCurrent = TraceIdCodec.normalizeTraceId(currentTraceId);
        if (normalizedCurrent != null) {
            return normalizedCurrent;
        }
        return TraceIdCodec.resolveTraceId(traceparent);
    }

    private static Result<Void> build(Result<Void> body, String traceId, BiConsumer<String, String> headerWriter) {
        String resolvedTraceId = resolveTraceId(traceId, null);
        if (resolvedTraceId != null && !resolvedTraceId.isBlank()) {
            body.setTraceId(resolvedTraceId);
            if (headerWriter != null) {
                headerWriter.accept(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(resolvedTraceId));
            }
        }
        return body;
    }
}
