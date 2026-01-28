package com.nowcoder.community.gateway.filter;

// WebFilter 级别的 traceId 注入：确保安全链路提前生成/回写 traceId。
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null) {
            return chain.filter(exchange);
        }
        String traceId = TraceIdSupport.resolveTraceId(exchange.getRequest().getHeaders());
        String traceparent = TraceIdSupport.buildTraceparent(traceId);

        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACEPARENT, traceparent);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TraceIdSupport.HEADER_TRACE_ID, traceId)
                .header(TraceIdSupport.HEADER_TRACEPARENT, traceparent)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
