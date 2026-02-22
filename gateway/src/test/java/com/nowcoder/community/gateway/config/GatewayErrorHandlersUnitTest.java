package com.nowcoder.community.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.gateway.filter.TraceIdSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorHandlersUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void securityExceptionHandlerShouldWriteJsonWithTraceHeaders() throws Exception {
        ReactiveSecurityExceptionHandler handler = new ReactiveSecurityExceptionHandler(objectMapper);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerWebExchange exchange1 = exchangeWithTraceId(traceId);
        handler.commence(exchange1, new BadCredentialsException("bad")).block(Duration.ofSeconds(1));

        assertThat(exchange1.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange1.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
        assertThat(exchange1.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACEPARENT)).isNotBlank();
        JsonNode json1 = objectMapper.readTree(bodyAsString(exchange1));
        assertThat(json1.path("code").asInt()).isEqualTo(401);
        assertThat(json1.path("traceId").asText()).isEqualTo(traceId);
        assertThat(json1.path("timestamp").asLong()).isPositive();

        MockServerWebExchange exchange2 = exchangeWithTraceId(traceId);
        handler.handle(exchange2, new AccessDeniedException("denied")).block(Duration.ofSeconds(1));

        assertThat(exchange2.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange2.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
        assertThat(exchange2.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACEPARENT)).isNotBlank();
        JsonNode json2 = objectMapper.readTree(bodyAsString(exchange2));
        assertThat(json2.path("code").asInt()).isEqualTo(403);
        assertThat(json2.path("traceId").asText()).isEqualTo(traceId);
        assertThat(json2.path("timestamp").asLong()).isPositive();
    }

    @Test
    void gatewayErrorHandlerShouldMapIllegalArgumentExceptionTo400() throws Exception {
        GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(objectMapper);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerWebExchange exchange = exchangeWithTraceId(traceId);
        handler.handle(exchange, new IllegalArgumentException("bad request")).block(Duration.ofSeconds(1));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACEPARENT)).isNotBlank();
        JsonNode json = objectMapper.readTree(bodyAsString(exchange));
        assertThat(json.path("code").asInt()).isEqualTo(400);
        assertThat(json.path("traceId").asText()).isEqualTo(traceId);
        assertThat(json.path("timestamp").asLong()).isPositive();
    }

    private static MockServerWebExchange exchangeWithTraceId(String traceId) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/__test__")
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .build();
        return MockServerWebExchange.from(request);
    }

    private static String bodyAsString(MockServerWebExchange exchange) {
        Mono<DataBuffer> joined = DataBufferUtils.join(exchange.getResponse().getBody());
        DataBuffer dataBuffer = joined.block(Duration.ofSeconds(1));
        if (dataBuffer == null) {
            return "";
        }
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

