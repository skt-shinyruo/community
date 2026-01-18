package com.nowcoder.community.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.gateway.config.GatewayRateLimitProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Component
public class GatewayRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitGlobalFilter.class);

    private static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    private static final String HEADER_RATE_LIMIT_RULE = "X-RateLimit-Rule";

    private final GatewayRateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRateLimitGlobalFilter(
            GatewayRateLimitProperties properties,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || properties.getRules() == null || properties.getRules().isEmpty()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (!StringUtils.hasText(path) || method == null) {
            return chain.filter(exchange);
        }

        GatewayRateLimitProperties.Rule rule = matchRule(method, path);
        if (rule == null) {
            return chain.filter(exchange);
        }

        int windowSeconds = Math.max(1, rule.getWindowSeconds());
        int maxRequests = Math.max(1, rule.getMaxRequests());

        return resolveKey(exchange, request, rule.getKeyStrategy())
                .defaultIfEmpty("anonymous")
                .flatMap(key -> checkAndApply(exchange, chain, rule, key, windowSeconds, maxRequests));
    }

    private GatewayRateLimitProperties.Rule matchRule(HttpMethod method, String path) {
        for (GatewayRateLimitProperties.Rule rule : properties.getRules()) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }
            if (!matchMethod(method, rule.getMethods())) {
                continue;
            }
            if (!matchPath(path, rule.getPathPatterns())) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private boolean matchMethod(HttpMethod method, List<String> configuredMethods) {
        if (configuredMethods == null || configuredMethods.isEmpty()) {
            return true;
        }
        String m = method.name();
        for (String allowed : configuredMethods) {
            if (!StringUtils.hasText(allowed)) {
                continue;
            }
            if (m.equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchPath(String path, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            if (pathMatcher.match(pattern.trim(), path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<String> resolveKey(ServerWebExchange exchange, ServerHttpRequest request, GatewayRateLimitProperties.KeyStrategy strategy) {
        GatewayRateLimitProperties.KeyStrategy resolved = strategy == null ? GatewayRateLimitProperties.KeyStrategy.IP : strategy;
        return switch (resolved) {
            case IP -> Mono.justOrEmpty(extractClientIp(request));
            case USER -> exchange.getPrincipal()
                    .map(principal -> principal instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : null)
                    .filter(StringUtils::hasText);
            case USER_OR_IP -> exchange.getPrincipal()
                    .map(principal -> principal instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : null)
                    .filter(StringUtils::hasText)
                    .switchIfEmpty(Mono.justOrEmpty(extractClientIp(request)));
        };
    }

    private Mono<Void> checkAndApply(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            GatewayRateLimitProperties.Rule rule,
            String key,
            int windowSeconds,
            int maxRequests
    ) {
        String ruleId = StringUtils.hasText(rule.getId()) ? rule.getId().trim() : "unnamed";
        String safeKey = key.trim().toLowerCase(Locale.ROOT);

        long nowSeconds = Instant.now().getEpochSecond();
        long bucket = nowSeconds / windowSeconds;
        long resetAt = (bucket + 1) * windowSeconds;

        String redisKey = "gateway:ratelimit:" + ruleId + ":" + bucket + ":" + safeKey;

        return redisTemplate.opsForValue().increment(redisKey)
                .flatMap(count -> {
                    long current = count == null ? 0 : count;
                    if (current == 1) {
                        return redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds + 5L)).thenReturn(current);
                    }
                    return Mono.just(current);
                })
                .flatMap(current -> {
                    long remaining = Math.max(0, maxRequests - current);
                    setRateLimitHeaders(exchange, ruleId, maxRequests, remaining, resetAt);

                    if (current > maxRequests) {
                        meterRegistry.counter(
                                "gateway_rate_limit_total",
                                Tags.of("rule", ruleId, "outcome", "blocked")
                        ).increment();
                        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, Result.error(CommonErrorCode.TOO_MANY_REQUESTS));
                    }

                    meterRegistry.counter(
                            "gateway_rate_limit_total",
                            Tags.of("rule", ruleId, "outcome", "allowed")
                    ).increment();
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    meterRegistry.counter(
                            "gateway_rate_limit_total",
                            Tags.of("rule", ruleId, "outcome", "error")
                    ).increment();

                    if (properties.isFailOpen()) {
                        log.warn("[ratelimit] redis error, fail-open (ruleId={}): {}", ruleId, ex.toString());
                        return chain.filter(exchange);
                    }

                    log.warn("[ratelimit] redis error, fail-closed (ruleId={}): {}", ruleId, ex.toString());
                    return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, Result.error(CommonErrorCode.SERVICE_UNAVAILABLE));
                });
    }

    private void setRateLimitHeaders(ServerWebExchange exchange, String ruleId, long limit, long remaining, long resetAtEpochSeconds) {
        exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_RULE, ruleId);
        exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_LIMIT, Long.toString(limit));
        exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_REMAINING, Long.toString(remaining));
        exchange.getResponse().getHeaders().set(HEADER_RATE_LIMIT_RESET, Long.toString(resetAtEpochSeconds));
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Result<?> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }

    private String extractClientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        InetSocketAddress addr = request.getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return null;
        }
        return addr.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        // 尽量靠前：在路由转发前拦截；但要晚于 trace 注入
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}

