package com.nowcoder.community.gateway.filter;

import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.BodyInserters;

import java.net.InetSocketAddress;
import java.time.LocalDate;

@Component
public class AnalyticsCollectGlobalFilter implements GlobalFilter, Ordered {

    private final AnalyticsCollectProperties properties;
    private final WebClient.Builder webClientBuilder;

    public AnalyticsCollectGlobalFilter(AnalyticsCollectProperties properties, WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getInternalToken())) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (path == null || !path.startsWith("/api/") || path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        String ip = extractClientIp(request);
        recordUv(ip);

        return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof JwtAuthenticationToken token) {
                        String userId = token.getToken().getSubject();
                        recordDau(userId);
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private void recordUv(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        webClientBuilder.build()
                .post()
                .uri("lb://analytics-service/internal/analytics/uv/record")
                .header("X-Internal-Token", properties.getInternalToken())
                .body(BodyInserters.fromFormData("ip", ip).with("date", LocalDate.now().toString()))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private void recordDau(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        webClientBuilder.build()
                .post()
                .uri("lb://analytics-service/internal/analytics/dau/record")
                .header("X-Internal-Token", properties.getInternalToken())
                .body(BodyInserters.fromFormData("userId", userId).with("date", LocalDate.now().toString()))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty())
                .subscribe();
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
        return Ordered.LOWEST_PRECEDENCE;
    }
}
