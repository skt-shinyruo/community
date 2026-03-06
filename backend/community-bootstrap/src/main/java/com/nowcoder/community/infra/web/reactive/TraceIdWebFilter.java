package com.nowcoder.community.infra.web.reactive;

import com.nowcoder.community.contracts.trace.TraceHeaders;
import com.nowcoder.community.contracts.trace.TraceIdCodec;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux traceId 注入：
 * - 解析 X-Trace-Id / traceparent（若缺失或非法则生成）
 * - 回写到响应头并透传到下游（用于 gateway 或未来 reactive 服务）
 *
 * <p>注意：reactive 链路禁止依赖 ThreadLocal/MDC 作为主传递机制；这里只做协议层 header 透传。</p>
 */
public class TraceIdWebFilter implements WebFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null) {
            return chain.filter(exchange);
        }
        HttpHeaders headers = exchange.getRequest() == null ? null : exchange.getRequest().getHeaders();
        String traceId = TraceIdCodec.resolveTraceId(
                headers == null ? null : headers.getFirst(TraceHeaders.HEADER_TRACE_ID),
                headers == null ? null : headers.getFirst(TraceHeaders.HEADER_TRACEPARENT)
        );
        String traceparent = TraceIdCodec.buildTraceparent(traceId);

        exchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, traceparent);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TraceHeaders.HEADER_TRACE_ID, traceId)
                .header(TraceHeaders.HEADER_TRACEPARENT, traceparent)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
