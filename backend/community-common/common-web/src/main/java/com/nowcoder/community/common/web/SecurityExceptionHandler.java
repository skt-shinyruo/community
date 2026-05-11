package com.nowcoder.community.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.security.response.SecurityResponseSupport;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceId;
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
        write(response, SecurityResponseSupport.unauthorized(resolveTraceId(request), response::setHeader));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, SecurityResponseSupport.forbidden(resolveTraceId(request), response::setHeader));
    }

    private String resolveTraceId(HttpServletRequest request) {
        return SecurityResponseSupport.resolveTraceId(
                TraceId.get(),
                request == null ? null : request.getHeader(TraceHeaders.HEADER_TRACEPARENT)
        );
    }

    private void write(HttpServletResponse response, Result<?> body) throws IOException {
        response.setStatus(body.getHttpStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
