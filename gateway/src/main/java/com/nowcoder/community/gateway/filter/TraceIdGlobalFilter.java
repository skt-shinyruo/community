package com.nowcoder.community.gateway.filter;

import com.nowcoder.community.common.trace.TraceId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceId.HEADER_NAME);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceId.generate();
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TraceId.HEADER_NAME, traceId)
                .build();

        exchange.getResponse().getHeaders().set(TraceId.HEADER_NAME, traceId);
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

