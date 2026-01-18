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
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID)).isNotBlank();
    }
}

