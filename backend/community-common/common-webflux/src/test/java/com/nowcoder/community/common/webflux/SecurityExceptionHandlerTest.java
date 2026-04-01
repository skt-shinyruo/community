package com.nowcoder.community.common.webflux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.exception.CommonErrorCode;
import com.nowcoder.community.common.trace.TraceHeaders;
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
                .header(TraceHeaders.HEADER_TRACE_ID, "ABCDEFABCDEFABCDEFABCDEFABCDEFAB")
                .build());

        StepVerifier.create(handler.commence(exchange, null)).verifyComplete();

        JsonNode body = readBody(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void handle_shouldWriteUnifiedForbiddenResult() {
        SecurityExceptionHandler handler = new SecurityExceptionHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/secure")
                .header(TraceHeaders.HEADER_TRACE_ID, "ABCDEFABCDEFABCDEFABCDEFABCDEFAB")
                .build());

        StepVerifier.create(handler.handle(exchange, new AccessDeniedException("denied"))).verifyComplete();

        JsonNode body = readBody(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID))
                .isEqualTo("abcdefabcdefabcdefabcdefabcdefab");
        assertThat(body.path("code").asInt()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
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
