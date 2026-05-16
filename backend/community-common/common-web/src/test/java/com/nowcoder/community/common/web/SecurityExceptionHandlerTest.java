package com.nowcoder.community.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void commence_shouldWriteResultJsonAndTraceHeaders() throws Exception {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (Scope ignored = activeSpan()) {
            handler.commence(request, response, null);
        }

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
        assertThat(body.path("traceId").asText()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    @Test
    void handle_shouldWriteForbiddenResult() throws Exception {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (Scope ignored = activeSpan()) {
            handler.handle(request, response, new AccessDeniedException("denied"));
        }

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(body.path("traceId").asText()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    private Scope activeSpan() {
        SpanContext spanContext = SpanContext.create(
                "abcdefabcdefabcdefabcdefabcdefab",
                "1234567890abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        return Span.wrap(spanContext).makeCurrent();
    }
}
