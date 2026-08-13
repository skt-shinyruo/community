package com.nowcoder.community.gateway.edge;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RateLimitWebFilter implements WebFilter, Ordered {

    /**
     * Run immediately after Spring Security so authenticated principals are visible to the key builder.
     */
    static final int SECURITY_WEB_FILTER_CHAIN_ORDER = -100;
    public static final int ORDER = SECURITY_WEB_FILTER_CHAIN_ORDER + 1;

    private final RateLimitProperties properties;
    private final RateLimiter limiter;
    private volatile PolicyPatternSnapshot policyPatternSnapshot;

    public RateLimitWebFilter(RateLimitProperties properties, RateLimiter limiter) {
        this.properties = properties;
        this.limiter = limiter;
        List<String> policyKeys = currentPolicyKeys(properties);
        this.policyPatternSnapshot = new PolicyPatternSnapshot(
                policyKeys,
                compilePolicyPatterns(policyKeys)
        );
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange == null || chain == null) {
            return Mono.empty();
        }
        String path = exchange.getRequest().getPath().value();
        PolicyMatch match = findPolicy(path);
        if (properties == null || !properties.isEnabled() || match == null || !match.policy().isEnabled()) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .map(principal -> principal == null ? "" : principal.getName())
                .filter(StringUtils::hasText)
                .map(name -> "principal:" + name + ":" + match.policyKey())
                .switchIfEmpty(Mono.just(remoteAddressKey(exchange, match.policyKey())))
                .flatMap(key -> applyPolicy(exchange, chain, key, match.policy()));
    }

    private PolicyMatch findPolicy(String path) {
        if (properties == null || properties.getPolicies().isEmpty()) {
            return null;
        }
        RateLimitProperties.Policy exact = properties.getPolicies().get(path);
        if (exact != null) {
            return new PolicyMatch(path, exact);
        }
        PathContainer requestPath = PathContainer.parsePath(path);
        Map<String, RateLimitProperties.Policy> policies = properties.getPolicies();
        return currentPolicyPatterns().stream()
                .filter(candidate -> candidate.pattern().matches(requestPath))
                .min(Comparator.comparing(PolicyPattern::pattern, PathPattern.SPECIFICITY_COMPARATOR))
                .flatMap(candidate -> Optional.ofNullable(policies.get(candidate.policyKey()))
                        .map(policy -> new PolicyMatch(candidate.policyKey(), policy)))
                .orElse(null);
    }

    private List<PolicyPattern> currentPolicyPatterns() {
        List<String> policyKeys = currentPolicyKeys(properties);
        PolicyPatternSnapshot snapshot = policyPatternSnapshot;
        if (snapshot.policyKeys().equals(policyKeys)) {
            return snapshot.patterns();
        }
        synchronized (this) {
            snapshot = policyPatternSnapshot;
            if (!snapshot.policyKeys().equals(policyKeys)) {
                snapshot = new PolicyPatternSnapshot(policyKeys, compilePolicyPatterns(policyKeys));
                policyPatternSnapshot = snapshot;
            }
            return snapshot.patterns();
        }
    }

    private static List<String> currentPolicyKeys(RateLimitProperties properties) {
        if (properties == null || properties.getPolicies().isEmpty()) {
            return List.of();
        }
        return properties.getPolicies().keySet().stream().sorted().toList();
    }

    private static List<PolicyPattern> compilePolicyPatterns(List<String> policyKeys) {
        PathPatternParser parser = new PathPatternParser();
        return policyKeys.stream()
                .map(policyKey -> compilePolicyPattern(parser, policyKey))
                .toList();
    }

    private static PolicyPattern compilePolicyPattern(
            PathPatternParser parser,
            String policyKey
    ) {
        if (!StringUtils.hasText(policyKey)) {
            throw new IllegalArgumentException("rate limit policy path must not be blank");
        }
        try {
            return new PolicyPattern(parser.parse(policyKey), policyKey);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid rate limit policy path: " + policyKey, exception);
        }
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

    private record PolicyPattern(
            PathPattern pattern,
            String policyKey
    ) {
    }

    private record PolicyPatternSnapshot(List<String> policyKeys, List<PolicyPattern> patterns) {
    }

    private record PolicyMatch(String policyKey, RateLimitProperties.Policy policy) {
    }
}
