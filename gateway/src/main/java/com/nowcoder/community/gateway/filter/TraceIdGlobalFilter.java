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
        String traceIdRaw = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        String traceparentRaw = exchange.getRequest().getHeaders().getFirst(HEADER_TRACEPARENT);

        String traceId = normalizeTraceId(traceIdRaw);
        String traceparentTraceId = extractTraceIdFromTraceparent(traceparentRaw);

        if (traceId == null) {
            traceId = traceparentTraceId;
        }
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 始终输出/透传规范化的 traceparent，避免上游 traceparent 使用大写 hex 导致断链路。
        String traceparent = buildTraceparent(traceId);

        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(HEADER_TRACEPARENT, traceparent);

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HEADER_TRACE_ID, traceId)
                .header(HEADER_TRACEPARENT, traceparent)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        String t = traceId.trim();
        if (t.length() != 32) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!ok) {
                return null;
            }
        }
        return t.toLowerCase();
    }

    private String extractTraceIdFromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        return normalizeTraceId(parts[1]);
    }

    private String buildTraceparent(String traceId) {
        // 00-<trace-id>-<span-id>-01
        String t = normalizeTraceId(traceId);
        if (t == null) {
            // defensive: should never happen (traceId is always generated/normalized before calling)
            t = UUID.randomUUID().toString().replace("-", "");
        }

        String spanId = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
        spanId = spanId.replace("-", "");
        if (spanId.length() < 16) {
            spanId = "0".repeat(16 - spanId.length()) + spanId;
        } else if (spanId.length() > 16) {
            spanId = spanId.substring(spanId.length() - 16);
        }
        spanId = spanId.toLowerCase();
        return "00-" + t + "-" + spanId + "-01";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
