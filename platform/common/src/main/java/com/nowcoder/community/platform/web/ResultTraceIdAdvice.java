package com.nowcoder.community.platform.web;

import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.trace.TraceHeaders;
import com.nowcoder.community.platform.trace.TraceId;
import com.nowcoder.community.contracts.trace.TraceIdCodec;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Result 响应 traceId 自动回填（Servlet 场景）：
 * - contracts 不再隐式读取 ThreadLocal，因此在 HTTP 出口统一补全 traceId
 * - 对安全异常（SecurityExceptionHandler 直写响应）不生效，需由 SecurityExceptionHandler 自己回填
 */
public class ResultTraceIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (!(body instanceof Result<?> result)) {
            return body;
        }
        if (result.getTraceId() != null && !result.getTraceId().isBlank()) {
            return body;
        }

        String traceId = TraceId.get();
        if (traceId == null || traceId.isBlank()) {
            String headerTraceId = request == null ? null : request.getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID);
            String traceparent = request == null ? null : request.getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
            traceId = TraceIdCodec.resolveTraceId(headerTraceId, traceparent);
        }
        traceId = traceId == null ? "" : traceId.trim();
        if (!traceId.isEmpty()) {
            result.setTraceId(traceId);
            if (response != null) {
                response.getHeaders().set(TraceHeaders.HEADER_TRACE_ID, traceId);
                response.getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(traceId));
            }
        }
        return body;
    }
}

