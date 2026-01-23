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

    private static final int MAX_UV_KEYS_PER_DAY = 200_000;
    private static final int MAX_DAU_KEYS_PER_DAY = 200_000;

    private static volatile LocalDate currentDay = LocalDate.now();
    private static final java.util.Set<String> seenUvKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> seenDauKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        // 浏览器预检会显著放大采集流量，且不代表真实访问。
        if (request.getMethod() == null || org.springframework.http.HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        LocalDate today = LocalDate.now();
        maybeRotateDay(today);

        String ip = extractClientIp(request);
        if (shouldRecordUv(today, ip)) {
            recordUv(ip, today);
        }

        return exchange.getPrincipal()
                .flatMap(principal -> {
                    if (principal instanceof JwtAuthenticationToken token) {
                        String userId = token.getToken().getSubject();
                        if (shouldRecordDau(today, userId)) {
                            recordDau(userId, today);
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private static void maybeRotateDay(LocalDate today) {
        LocalDate d = currentDay;
        if (d.equals(today)) {
            return;
        }
        synchronized (AnalyticsCollectGlobalFilter.class) {
            if (!currentDay.equals(today)) {
                currentDay = today;
                seenUvKeys.clear();
                seenDauKeys.clear();
            }
        }
    }

    private boolean shouldRecordUv(LocalDate today, String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        // 同一 gateway 实例内做“当天去重”，避免每个请求都打 analytics-service。
        String key = today.toString() + ":" + ip.trim();
        boolean first = seenUvKeys.add(key);
        if (seenUvKeys.size() > MAX_UV_KEYS_PER_DAY) {
            // 防御性：避免异常流量导致内存膨胀。超过阈值时退化为“少量重复采集”。
            seenUvKeys.clear();
        }
        return first;
    }

    private boolean shouldRecordDau(LocalDate today, String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        String key = today.toString() + ":" + userId.trim();
        boolean first = seenDauKeys.add(key);
        if (seenDauKeys.size() > MAX_DAU_KEYS_PER_DAY) {
            seenDauKeys.clear();
        }
        return first;
    }

    private void recordUv(String ip, LocalDate date) {
        if (!StringUtils.hasText(ip) || date == null) {
            return;
        }
        webClientBuilder.build()
                .post()
                .uri("lb://analytics-service/internal/analytics/uv/record")
                .header("X-Internal-Token", properties.getInternalToken())
                .body(BodyInserters.fromFormData("ip", ip).with("date", date.toString()))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private void recordDau(String userId, LocalDate date) {
        if (!StringUtils.hasText(userId) || date == null) {
            return;
        }
        webClientBuilder.build()
                .post()
                .uri("lb://analytics-service/internal/analytics/dau/record")
                .header("X-Internal-Token", properties.getInternalToken())
                .body(BodyInserters.fromFormData("userId", userId).with("date", date.toString()))
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
