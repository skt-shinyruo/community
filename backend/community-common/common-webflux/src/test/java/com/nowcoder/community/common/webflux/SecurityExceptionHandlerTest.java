package com.nowcoder.community.common.webflux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceHeaders;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void commence_shouldWriteUnifiedUnauthorizedResult() {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/secure")
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent("abcdefabcdefabcdefabcdefabcdefab"))
                .build());

        try (Scope ignored = activeSpan()) {
            StepVerifier.create(handler.commence(exchange, null)).verifyComplete();
        }

        JsonNode body = readBody(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
        assertThat(body.path("traceId").asText()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    @Test
    void handle_shouldWriteUnifiedForbiddenResult() {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/secure")
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent("abcdefabcdefabcdefabcdefabcdefab"))
                .build());

        try (Scope ignored = activeSpan()) {
            StepVerifier.create(handler.handle(exchange, new AccessDeniedException("denied"))).verifyComplete();
        }

        JsonNode body = readBody(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo("00-abcdefabcdefabcdefabcdefabcdefab-1234567890abcdef-01");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
        assertThat(body.path("traceId").asText()).isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
    }

    private static String traceparent(String traceId) {
        return "00-" + traceId + "-00f067aa0ba902b7-01";
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

    private JsonNode readBody(MockServerWebExchange exchange) {
        byte[] bytes = exchange.getResponse().getBodyAsString().block().getBytes();
        try {
            return objectMapper.readTree(bytes);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
