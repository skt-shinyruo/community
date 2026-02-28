package com.nowcoder.community.gateway.filter;

// 网关异常响应 traceId 测试：验证 401/403/429 响应体与头部一致。
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.gateway.config.GatewayErrorWebExceptionHandler;
import com.nowcoder.community.gateway.config.GatewayRateLimitProperties;
import com.nowcoder.community.platform.web.reactive.ReactiveSecurityExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayErrorTraceIdTest {

    @Test
    void shouldWriteTraceIdOnUnauthorizedAndForbidden() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ReactiveSecurityExceptionHandler handler = new ReactiveSecurityExceptionHandler(objectMapper);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.commence(exchange, new BadCredentialsException("bad")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String body = exchange.getResponse().getBodyAsString().block();
        Map<?, ?> payload = objectMapper.readValue(body, Map.class);
        assertThat(payload.get("traceId")).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);

        MockServerWebExchange forbiddenExchange = MockServerWebExchange.from(request);
        handler.handle(forbiddenExchange, new AccessDeniedException("denied")).block();
        assertThat(forbiddenExchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String forbiddenBody = forbiddenExchange.getResponse().getBodyAsString().block();
        Map<?, ?> forbiddenPayload = objectMapper.readValue(forbiddenBody, Map.class);
        assertThat(forbiddenPayload.get("traceId")).isEqualTo(traceId);
        assertThat(forbiddenExchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
    }

    @Test
    void shouldWriteTraceIdOnBadRequest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(objectMapper);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new IllegalArgumentException("bad")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String body = exchange.getResponse().getBodyAsString().block();
        Map<?, ?> payload = objectMapper.readValue(body, Map.class);
        assertThat(payload.get("code")).isEqualTo(CommonErrorCode.INVALID_ARGUMENT.getCode());
        assertThat(payload.get("traceId")).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
    }

    @Test
    void shouldWriteTraceIdOnInternalError() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GatewayErrorWebExceptionHandler handler = new GatewayErrorWebExceptionHandler(objectMapper);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        handler.handle(exchange, new RuntimeException("boom")).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        String body = exchange.getResponse().getBodyAsString().block();
        Map<?, ?> payload = objectMapper.readValue(body, Map.class);
        assertThat(payload.get("code")).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        assertThat(payload.get("traceId")).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
    }

    @Test
    void shouldWriteTraceIdOnRateLimit() throws Exception {
        GatewayRateLimitProperties properties = new GatewayRateLimitProperties();
        GatewayRateLimitProperties.Rule rule = new GatewayRateLimitProperties.Rule();
        rule.setId("test");
        rule.setEnabled(true);
        rule.setMethods(List.of(HttpMethod.POST.name()));
        rule.setPathPatterns(List.of("/api/**"));
        rule.setWindowSeconds(60);
        rule.setMaxRequests(1);
        rule.setKeyStrategy(GatewayRateLimitProperties.KeyStrategy.IP);
        properties.setRules(List.of(rule));

        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.just(2L));

        ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
        when(clientIpResolver.resolve(org.mockito.ArgumentMatchers.any(ServerHttpRequest.class))).thenReturn("10.0.0.1");

        GatewayRateLimitGlobalFilter filter = new GatewayRateLimitGlobalFilter(
                properties,
                redisTemplate,
                new ObjectMapper(),
                new SimpleMeterRegistry(),
                clientIpResolver
        );

        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/posts")
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = webExchange -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String body = exchange.getResponse().getBodyAsString().block();
        Map<?, ?> payload = new ObjectMapper().readValue(body, Map.class);
        assertThat(payload.get("traceId")).isEqualTo(traceId);
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID)).isEqualTo(traceId);
    }
}
