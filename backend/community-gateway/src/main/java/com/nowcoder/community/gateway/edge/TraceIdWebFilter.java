package com.nowcoder.community.gateway.edge;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class TraceIdWebFilter implements WebFilter, Ordered {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String TRACEPARENT_HEADER = "traceparent";
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        String existingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String existingTraceparent = exchange.getRequest().getHeaders().getFirst(TRACEPARENT_HEADER);
        String traceId = TraceIdCodec.resolveTraceId(existingTraceId, existingTraceparent);
        String traceparent = TraceIdCodec.extractTraceIdFromTraceparent(existingTraceparent) == null
                ? TraceIdCodec.buildTraceparent(traceId)
                : existingTraceparent.trim();
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set(TRACE_ID_HEADER, traceId);
                    headers.set(TRACEPARENT_HEADER, traceparent);
                })
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(mutatedExchange);
    }
}
