package com.nowcoder.community.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdGlobalFilterTest {

    @Test
    void shouldPropagateTraceIdToDownstreamAndResponse() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/me").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = (webExchange) -> {
            ServerHttpRequest downstream = webExchange.getRequest();
            assertThat(downstream.getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID)).isNotBlank();
            assertThat(downstream.getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACEPARENT)).isNotBlank();
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID)).isNotBlank();
        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACEPARENT)).isNotBlank();
    }

    @Test
    void shouldAcceptUppercaseTraceparentAndNormalizeToLowercase() {
        TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/posts")
                .header(TraceIdGlobalFilter.HEADER_TRACEPARENT, "00-4BF92F3577B34DA6A3CE929D0E0E4736-00F067AA0BA902B7-01")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, webExchange -> Mono.empty()).block();

        String traceId = exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID);
        String traceparent = exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACEPARENT);

        assertThat(traceId).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(traceparent).startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
        assertThat(traceparent).endsWith("-01");
    }
}

