package com.nowcoder.community.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuditLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuditLogGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (path == null || !path.startsWith("/api/") || method == null) {
            return chain.filter(exchange);
        }
        if (HttpMethod.GET.equals(method) || HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }
        // 避免在网关层记录敏感登录参数
        if (path.startsWith("/api/auth/login")) {
            return chain.filter(exchange);
        }

        String traceId = request.getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID);
        long startNanos = System.nanoTime();

        return exchange.getPrincipal()
                .map(principal -> principal instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : "-")
                .defaultIfEmpty("-")
                .flatMap(userId -> chain.filter(exchange)
                        .doFinally(signal -> {
                            long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
                            Integer status = exchange.getResponse().getStatusCode() == null
                                    ? 200
                                    : exchange.getResponse().getStatusCode().value();
                            log.info("[audit][gateway] method={} path={} status={} userId={} traceId={} costMs={}",
                                    method.name(), path, status, userId, traceId, costMs);
                        }));
    }

    @Override
    public int getOrder() {
        // 在 TraceId 注入后执行，便于关联
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
