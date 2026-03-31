package com.nowcoder.community.common.web;

import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Result 响应自动包装与 traceId 回填（Servlet 场景）：
 * - 普通 JSON 成功响应统一包装为 Result.ok(data)
 * - contracts 不再隐式读取 ThreadLocal，因此在 HTTP 出口统一补全 traceId
 * - 对安全异常（SecurityExceptionHandler 直写响应）不生效，需由 SecurityExceptionHandler 自己回填
 */
public class ResultTraceIdAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (returnType == null) {
            return true;
        }
        if (converterType != null && StringHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }
        return !shouldSkipReturnType(returnType);
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
        if (shouldSkipBody(body)) {
            return body;
        }

        Result<?> result;
        if (body instanceof Result<?> wrapped) {
            result = wrapped;
        } else {
            result = Result.ok(body);
        }

        fillTraceId(result, request, response);
        return result;
    }

    private boolean shouldSkipReturnType(MethodParameter returnType) {
        ResolvableType type = ResolvableType.forMethodParameter(returnType);
        Class<?> rawType = type.resolve(returnType.getParameterType());
        if (rawType == null) {
            return false;
        }
        if (Void.TYPE.equals(rawType) || Void.class.equals(rawType)) {
            return true;
        }
        if (HttpEntity.class.isAssignableFrom(rawType)) {
            Class<?> bodyType = type.getGeneric(0).resolve();
            if (bodyType == null) {
                return true;
            }
            return !Result.class.isAssignableFrom(bodyType);
        }
        return isSkippedPayloadType(rawType);
    }

    private boolean shouldSkipBody(Object body) {
        if (body == null) {
            return false;
        }
        return isSkippedPayloadType(body.getClass());
    }

    private boolean isSkippedPayloadType(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (CharSequence.class.isAssignableFrom(type)) {
            return true;
        }
        if (type.isArray() && byte.class.equals(type.getComponentType())) {
            return true;
        }
        return Resource.class.isAssignableFrom(type)
                || ResponseBodyEmitter.class.isAssignableFrom(type)
                || SseEmitter.class.isAssignableFrom(type)
                || StreamingResponseBody.class.isAssignableFrom(type);
    }

    private void fillTraceId(Result<?> result, ServerHttpRequest request, ServerHttpResponse response) {
        if (result == null || (result.getTraceId() != null && !result.getTraceId().isBlank())) {
            return;
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
    }
}
