package com.nowcoder.community.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_TRACEPARENT = "traceparent";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        String traceparent = exchange.getRequest().getHeaders().getFirst(HEADER_TRACEPARENT);

        if (traceId == null || traceId.isBlank()) {
            traceId = extractTraceIdFromTraceparent(traceparent);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        if (traceparent == null || traceparent.isBlank() || extractTraceIdFromTraceparent(traceparent) == null) {
            traceparent = buildTraceparent(traceId);
        }

        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(HEADER_TRACEPARENT, traceparent);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_TRACE_ID, traceId)
                .header(HEADER_TRACEPARENT, traceparent)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private String extractTraceIdFromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        String traceId = parts[1];
        if (traceId == null || traceId.length() != 32) {
            return null;
        }
        for (int i = 0; i < traceId.length(); i++) {
            char c = traceId.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!ok) {
                return null;
            }
        }
        return traceId;
    }

    private String buildTraceparent(String traceId) {
        // 00-<trace-id>-<span-id>-01
        String spanId = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
        spanId = spanId.replace("-", "");
        if (spanId.length() < 16) {
            spanId = "0".repeat(16 - spanId.length()) + spanId;
        } else if (spanId.length() > 16) {
            spanId = spanId.substring(spanId.length() - 16);
        }
        return "00-" + traceId + "-" + spanId + "-01";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
