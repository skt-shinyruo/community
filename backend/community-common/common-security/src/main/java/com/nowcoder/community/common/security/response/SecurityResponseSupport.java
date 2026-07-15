package com.nowcoder.community.common.security.response;

import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import com.nowcoder.community.common.web.Result;

import java.util.function.BiConsumer;

public final class SecurityResponseSupport {

    private SecurityResponseSupport() {
    }

    public static Result<Void> unauthorized(String traceId, BiConsumer<String, String> headerWriter) {
        return unauthorized(traceId, null, headerWriter);
    }

    public static Result<Void> unauthorized(String traceId, String traceparent, BiConsumer<String, String> headerWriter) {
        return build(Result.error(
                CommonErrorCode.UNAUTHORIZED.getCode(),
                CommonErrorCode.UNAUTHORIZED.getMessage(),
                401
        ), traceId, traceparent, headerWriter);
    }

    public static Result<Void> forbidden(String traceId, BiConsumer<String, String> headerWriter) {
        return forbidden(traceId, null, headerWriter);
    }

    public static Result<Void> forbidden(String traceId, String traceparent, BiConsumer<String, String> headerWriter) {
        return build(Result.error(
                CommonErrorCode.FORBIDDEN.getCode(),
                CommonErrorCode.FORBIDDEN.getMessage(),
                403
        ), traceId, traceparent, headerWriter);
    }

    public static String resolveTraceId(String currentTraceId, String traceparent) {
        String active = OtelTraceContext.currentTraceId();
        if (active != null) {
            return active;
        }
        String normalizedCurrent = TraceIdCodec.normalizeTraceId(currentTraceId);
        if (normalizedCurrent != null) {
            return normalizedCurrent;
        }
        String extracted = TraceIdCodec.extractTraceIdFromTraceparent(traceparent);
        if (extracted != null) {
            return extracted;
        }
        return TraceContextSnapshot.currentOrNew().traceId();
    }

    private static Result<Void> build(Result<Void> body, String traceId, String traceparent, BiConsumer<String, String> headerWriter) {
        String activeTraceparent = OtelTraceContext.currentTraceparent();
        String fallbackTraceparent = activeTraceparent == null ? traceparent : activeTraceparent;
        String resolvedTraceId = resolveTraceId(traceId, fallbackTraceparent);
        if (resolvedTraceId != null && !resolvedTraceId.isBlank()) {
            body.setTraceId(resolvedTraceId);
            if (headerWriter != null) {
                String headerTraceparent = fallbackTraceparent;
                if (!resolvedTraceId.equals(TraceIdCodec.extractTraceIdFromTraceparent(headerTraceparent))) {
                    headerTraceparent = TraceIdCodec.buildTraceparent(resolvedTraceId);
                }
                headerWriter.accept(TraceHeaders.HEADER_TRACEPARENT, headerTraceparent);
            }
        }
        return body;
    }
}
