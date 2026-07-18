package com.nowcoder.community.gateway.edge;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class RateLimitWebFilter implements WebFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 30;

    private final RateLimitProperties properties;
    private final RateLimiter limiter;

    public RateLimitWebFilter(RateLimitProperties properties, RateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        String path = exchange.getRequest().getPath().value();
        RateLimitProperties.Policy policy = properties == null ? null : properties.getPolicies().get(path);
        if (properties == null || !properties.isEnabled() || policy == null || !policy.isEnabled()) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .map(principal -> principal == null ? "" : principal.getName())
                .filter(StringUtils::hasText)
                .map(name -> "principal:" + name + ":" + path)
                .switchIfEmpty(Mono.just(remoteAddressKey(exchange, path)))
                .flatMap(key -> applyPolicy(exchange, chain, key, policy));
    }

    private Mono<Void> applyPolicy(
            ServerWebExchange exchange,
            WebFilterChain chain,
            String key,
            RateLimitProperties.Policy policy
    ) {
        try {
            if (limiter.allow(key, policy)) {
                return chain.filter(exchange);
            }
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        } catch (RuntimeException e) {
            if (properties.isFailOpenOnError()) {
                return chain.filter(exchange);
            }
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }

    private static String remoteAddressKey(ServerWebExchange exchange, String path) {
        String canonicalClientIp = exchange == null
                ? null
                : exchange.getAttribute(ForwardedHeaderCanonicalizationWebFilter.CANONICAL_CLIENT_IP_ATTRIBUTE);
        if (StringUtils.hasText(canonicalClientIp)) {
            return "ip:" + canonicalClientIp + ":" + path;
        }
        if (exchange == null || exchange.getRequest() == null || exchange.getRequest().getRemoteAddress() == null) {
            return "ip:unknown:" + path;
        }
        String host = exchange.getRequest().getRemoteAddress().getAddress() == null
                ? exchange.getRequest().getRemoteAddress().getHostString()
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return "ip:" + (StringUtils.hasText(host) ? host : "unknown") + ":" + path;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
