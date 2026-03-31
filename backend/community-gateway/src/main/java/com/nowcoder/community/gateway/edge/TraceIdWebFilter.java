package com.nowcoder.community.gateway.edge;

import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.trace.TraceIdCodec;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class TraceIdWebFilter implements WebFilter, Ordered {

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
        String existingTraceId = exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID);
        String existingTraceparent = exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        String traceId = TraceIdCodec.resolveTraceId(existingTraceId, existingTraceparent);
        String traceparent = TraceIdCodec.extractTraceIdFromTraceparent(existingTraceparent) == null
                ? TraceIdCodec.buildTraceparent(traceId)
                : existingTraceparent.trim();
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.set(TraceHeaders.HEADER_TRACE_ID, traceId);
                    headers.set(TraceHeaders.HEADER_TRACEPARENT, traceparent);
                })
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACE_ID, traceId);
        return chain.filter(mutatedExchange);
    }
}
