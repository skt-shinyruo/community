package com.nowcoder.community.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.web.Result;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
import com.nowcoder.community.common.trace.TraceIdCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException authException)
            throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED, Result.error(CommonErrorCode.UNAUTHORIZED));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, Result.error(CommonErrorCode.FORBIDDEN));
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int httpStatus, Result<?> body) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String traceId = TraceId.get();
        if (traceId == null || traceId.isBlank()) {
            String headerTraceId = request == null ? null : request.getHeader(TraceHeaders.HEADER_TRACE_ID);
            String traceparent = request == null ? null : request.getHeader(TraceHeaders.HEADER_TRACEPARENT);
            traceId = TraceIdCodec.resolveTraceId(headerTraceId, traceparent);
        }
        if (traceId != null && !traceId.isBlank()) {
            String t = traceId.trim();
            body.setTraceId(t);
            response.setHeader(TraceHeaders.HEADER_TRACE_ID, t);
            response.setHeader(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(t));
        }

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
