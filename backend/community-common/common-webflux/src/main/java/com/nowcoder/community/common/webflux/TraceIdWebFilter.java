package com.nowcoder.community.common.webflux;

import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class TraceIdWebFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }

        String incomingTraceparent = exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT);
        TraceContextSnapshot snapshot = OtelTraceContext.currentSpanContext() == null
                ? TraceContextSnapshot.fromInbound(incomingTraceparent)
                : TraceContextSnapshot.currentOrNew();

        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent()))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
        mutatedExchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, snapshot.traceparent());
        return chain.filter(mutatedExchange);
    }
}
