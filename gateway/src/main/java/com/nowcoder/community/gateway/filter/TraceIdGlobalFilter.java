package com.nowcoder.community.gateway.filter;

// Gateway 级别 traceId 透传：为路由链路统一写入规范化 traceId/traceparent。
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_TRACE_ID = TraceIdSupport.HEADER_TRACE_ID;
    public static final String HEADER_TRACEPARENT = TraceIdSupport.HEADER_TRACEPARENT;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceIdSupport.resolveTraceId(exchange.getRequest().getHeaders());
        String traceparent = TraceIdSupport.buildTraceparent(traceId);

        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(HEADER_TRACEPARENT, traceparent);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_TRACE_ID, traceId)
                .header(HEADER_TRACEPARENT, traceparent)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
