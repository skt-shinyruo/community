package com.nowcoder.community.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.platform.net.TrustedProxyProperties;
import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OriginGuardGlobalFilterTest {

    @Test
    void shouldRejectDisallowedOriginForSensitiveEndpoints() {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setEnabled(true);
        props.setAllowedOrigins(List.of("http://localhost:12881"));

        OriginGuardGlobalFilter filter = new OriginGuardGlobalFilter(props, new ObjectMapper(), new ForwardedOriginResolver(new TrustedProxyProperties()));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("Origin", "http://localhost:12888")
                .header(TraceIdSupport.HEADER_TRACE_ID, "0123456789abcdef0123456789abcdef")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = webExchange -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":403");
        assertThat(body).contains("Origin 不被允许");
        assertThat(body).contains("0123456789abcdef0123456789abcdef");
    }

    @Test
    void shouldAllowAllowedOrigin() {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setEnabled(true);
        props.setAllowedOrigins(List.of("http://localhost:12881"));

        OriginGuardGlobalFilter filter = new OriginGuardGlobalFilter(props, new ObjectMapper(), new ForwardedOriginResolver(new TrustedProxyProperties()));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/refresh")
                .header("Origin", "http://localhost:12881")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = webExchange -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void shouldAllowWhenOriginHeaderMissing() {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setEnabled(true);
        props.setAllowedOrigins(List.of("http://localhost:12881"));

        OriginGuardGlobalFilter filter = new OriginGuardGlobalFilter(props, new ObjectMapper(), new ForwardedOriginResolver(new TrustedProxyProperties()));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/logout").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = webExchange -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void shouldRejectWhenAllowlistEmptyAndFailClosed() {
        OriginGuardProperties props = new OriginGuardProperties();
        props.setEnabled(true);
        props.setFailOpenWhenAllowlistEmpty(false);
        props.setAllowedOrigins(List.of());

        OriginGuardGlobalFilter filter = new OriginGuardGlobalFilter(props, new ObjectMapper(), new ForwardedOriginResolver(new TrustedProxyProperties()));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header("Origin", "http://localhost:12881")
                .header(TraceIdSupport.HEADER_TRACE_ID, "0123456789abcdef0123456789abcdef")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean called = new AtomicBoolean(false);
        GatewayFilterChain chain = webExchange -> {
            called.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":403");
        assertThat(body).contains("Origin allowlist 未配置");
        assertThat(body).contains("0123456789abcdef0123456789abcdef");
    }
}
