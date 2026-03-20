package com.nowcoder.community.gateway.edge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class AccessLogWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        long startedAt = System.nanoTime();
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdWebFilter.TRACE_ID_HEADER);
        String method = exchange.getRequest().getMethod() == null ? "UNKNOWN" : exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        return chain.filter(exchange)
                .doFinally(signal -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
                    log.info("[gateway-http] method={} path={} status={} durationMs={} traceId={}",
                            method,
                            path,
                            status == null ? 0 : status.value(),
                            durationMs,
                            traceId == null ? "" : traceId);
                });
    }
}
