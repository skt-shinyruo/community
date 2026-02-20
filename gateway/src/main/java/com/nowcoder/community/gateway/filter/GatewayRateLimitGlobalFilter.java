package com.nowcoder.community.gateway.filter;

// 网关限流过滤器：支持多策略限流并统一写出 traceId。
import com.fasterxml.jackson.core.JsonProcessingException;
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

    private static final String ATTR_IP_SOURCE = "gateway.ip_source";

    private final GatewayRateLimitProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ClientIpResolver clientIpResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRateLimitGlobalFilter(
            GatewayRateLimitProperties properties,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (StringUtils.hasText(path) && method != null && !HttpMethod.OPTIONS.equals(method) && isBlockedPath(path)) {
            // “可关闭开关”：按 404 隐藏入口（避免暴露运维/高风险能力）
            return write(exchange, HttpStatus.NOT_FOUND, Result.error(CommonErrorCode.NOT_FOUND));
        }

        if (!properties.isEnabled() || properties.getRules() == null || properties.getRules().isEmpty()) {
            return chain.filter(exchange);
        }

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

    private boolean isBlockedPath(String path) {
        List<String> patterns = properties.getBlockedPathPatterns();
        if (patterns == null || patterns.isEmpty() || !StringUtils.hasText(path)) {
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
            case IP -> Mono.defer(() -> {
                ClientIpResolver.ResolvedClientIp ip = clientIpResolver.resolveWithSource(request);
                exchange.getAttributes().put(ATTR_IP_SOURCE, ip == null ? "unknown" : ip.source());
                return Mono.justOrEmpty(ip == null ? null : ip.ip());
            });
            case USER -> exchange.getPrincipal()
                    .doOnNext(p -> exchange.getAttributes().put(ATTR_IP_SOURCE, "na"))
                    .map(principal -> principal instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : null)
                    .filter(StringUtils::hasText);
            case USER_OR_IP -> exchange.getPrincipal()
                    .doOnNext(p -> exchange.getAttributes().put(ATTR_IP_SOURCE, "na"))
                    .map(principal -> principal instanceof JwtAuthenticationToken token ? token.getToken().getSubject() : null)
                    .filter(StringUtils::hasText)
                    .switchIfEmpty(Mono.defer(() -> {
                        ClientIpResolver.ResolvedClientIp ip = clientIpResolver.resolveWithSource(request);
                        exchange.getAttributes().put(ATTR_IP_SOURCE, ip == null ? "unknown" : ip.source());
                        return Mono.justOrEmpty(ip == null ? null : ip.ip());
                    }));
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
                    String ipSource = String.valueOf(exchange.getAttributes().getOrDefault(ATTR_IP_SOURCE, "na"));
                    long remaining = Math.max(0, maxRequests - current);
                    setRateLimitHeaders(exchange, ruleId, maxRequests, remaining, resetAt);

                    if (current > maxRequests) {
                        meterRegistry.counter(
                                "gateway_rate_limit_total",
                                Tags.of("rule", ruleId, "outcome", "blocked", "ip_source", ipSource)
                        ).increment();
                        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, Result.error(CommonErrorCode.TOO_MANY_REQUESTS));
                    }

                    meterRegistry.counter(
                            "gateway_rate_limit_total",
                            Tags.of("rule", ruleId, "outcome", "allowed", "ip_source", ipSource)
                    ).increment();
                    return chain.filter(exchange);
                })
                .onErrorResume(ex -> {
                    String ipSource = String.valueOf(exchange.getAttributes().getOrDefault(ATTR_IP_SOURCE, "na"));
                    meterRegistry.counter(
                            "gateway_rate_limit_total",
                            Tags.of("rule", ruleId, "outcome", "error", "ip_source", ipSource)
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
        String traceId = TraceIdSupport.resolveTraceId(exchange.getRequest().getHeaders());
        body.setTraceId(traceId);
        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACEPARENT, TraceIdSupport.buildTraceparent(traceId));
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 尽量靠前：在路由转发前拦截；但要晚于 trace 注入
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
