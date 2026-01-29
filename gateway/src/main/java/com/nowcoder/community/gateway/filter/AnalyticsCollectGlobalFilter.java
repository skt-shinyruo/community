package com.nowcoder.community.gateway.filter;

// 网关 UV/DAU 采集过滤器：基于可信代理模型解析客户端 IP，减少伪造风险。
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nowcoder.community.gateway.config.AnalyticsCollectProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AnalyticsCollectGlobalFilter implements GlobalFilter, Ordered {

    // in-process 去重缓存：只用于“降噪”，最终以 analytics-service Redis 去重/聚合为准。
    private final Object dayLock = new Object();
    private volatile LocalDate currentDay = LocalDate.now();
    private final Cache<String, Boolean> seenUvKeys;
    private final Cache<String, Boolean> seenDauKeys;

    private final AnalyticsCollectProperties properties;
    private final WebClient webClient;
    private final ClientIpResolver clientIpResolver;
    private final MeterRegistry meterRegistry;
    private final AtomicInteger inflight = new AtomicInteger();

    public AnalyticsCollectGlobalFilter(
            AnalyticsCollectProperties properties,
            WebClient.Builder webClientBuilder,
            ClientIpResolver clientIpResolver,
            MeterRegistry meterRegistry
    ) {
        this.properties = properties;
        this.webClient = webClientBuilder.build();
        this.clientIpResolver = clientIpResolver;
        this.meterRegistry = meterRegistry;

        Duration ttl = Duration.ofSeconds(Math.max(1, properties.getDedupTtlSeconds()));
        long uvMaxSize = Math.max(1, properties.getUvCacheMaxSize());
        long dauMaxSize = Math.max(1, properties.getDauCacheMaxSize());
        this.seenUvKeys = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(uvMaxSize).build();
        this.seenDauKeys = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(dauMaxSize).build();
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

        String traceId = request.getHeaders().getFirst(TraceIdSupport.HEADER_TRACE_ID);
        String traceparent = request.getHeaders().getFirst(TraceIdSupport.HEADER_TRACEPARENT);

        String ip = clientIpResolver.resolve(request);
        if (shouldRecordUv(today, ip)) {
            recordUv(ip, today, traceId, traceparent);
        }

        // analytics 采集必须“与转发链路隔离”：不能因为采集失败/鉴权上下文异常而影响请求转发。
        exchange.getPrincipal()
                .onErrorResume(e -> {
                    if (meterRegistry != null) {
                        meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", "dau", "outcome", "skipped_principal_error")).increment();
                    }
                    return Mono.empty();
                })
                .doOnNext(principal -> {
                    if (principal instanceof JwtAuthenticationToken token) {
                        Integer userId = parseUserId(token.getToken().getSubject());
                        if (userId != null && shouldRecordDau(today, userId)) {
                            recordDau(userId, today, traceId, traceparent);
                        }
                    }
                })
                .subscribe();

        return chain.filter(exchange);
    }

    private void maybeRotateDay(LocalDate today) {
        LocalDate d = currentDay;
        if (d.equals(today)) {
            return;
        }
        synchronized (dayLock) {
            if (!currentDay.equals(today)) {
                currentDay = today;
                seenUvKeys.invalidateAll();
                seenDauKeys.invalidateAll();
            }
        }
    }

    private boolean shouldRecordUv(LocalDate today, String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        if (!properties.isDedupEnabled()) {
            return true;
        }
        // 同一 gateway 实例内做“当天去重”，避免每个请求都打 analytics-service。
        String key = ip.trim();
        return seenUvKeys.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    private boolean shouldRecordDau(LocalDate today, int userId) {
        if (userId <= 0) {
            return false;
        }
        if (!properties.isDedupEnabled()) {
            return true;
        }
        String key = String.valueOf(userId);
        return seenDauKeys.asMap().putIfAbsent(key, Boolean.TRUE) == null;
    }

    private void recordUv(String ip, LocalDate date, String traceId, String traceparent) {
        if (!StringUtils.hasText(ip) || date == null) {
            return;
        }
        submitAsync(
                "uv",
                webClient.post()
                        .uri("lb://analytics-service/internal/analytics/uv/record")
                        .header("X-Internal-Token", properties.getInternalToken())
                        .headers(h -> {
                            if (StringUtils.hasText(traceId)) {
                                h.set(TraceIdSupport.HEADER_TRACE_ID, traceId);
                            }
                            if (StringUtils.hasText(traceparent)) {
                                h.set(TraceIdSupport.HEADER_TRACEPARENT, traceparent);
                            }
                        })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("ip", ip).with("date", date.toString()))
                        .retrieve()
                        .bodyToMono(Void.class)
        );
    }

    private void recordDau(int userId, LocalDate date, String traceId, String traceparent) {
        if (userId <= 0 || date == null) {
            return;
        }
        submitAsync(
                "dau",
                webClient.post()
                        .uri("lb://analytics-service/internal/analytics/dau/record")
                        .header("X-Internal-Token", properties.getInternalToken())
                        .headers(h -> {
                            if (StringUtils.hasText(traceId)) {
                                h.set(TraceIdSupport.HEADER_TRACE_ID, traceId);
                            }
                            if (StringUtils.hasText(traceparent)) {
                                h.set(TraceIdSupport.HEADER_TRACEPARENT, traceparent);
                            }
                        })
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData("userId", String.valueOf(userId)).with("date", date.toString()))
                        .retrieve()
                        .bodyToMono(Void.class)
        );
    }

    private Integer parseUserId(String subject) {
        if (!StringUtils.hasText(subject)) {
            return null;
        }
        try {
            int id = Integer.parseInt(subject.trim());
            return id > 0 ? id : null;
        } catch (Exception ignored) {
            if (meterRegistry != null) {
                meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", "dau", "outcome", "skipped_bad_subject")).increment();
            }
            return null;
        }
    }

    private void submitAsync(String metric, Mono<Void> call) {
        if (call == null) {
            return;
        }
        int max = Math.max(1, properties.getMaxConcurrency());
        int current = inflight.incrementAndGet();
        if (current > max) {
            inflight.decrementAndGet();
            meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", metric, "outcome", "skipped_concurrency")).increment();
            return;
        }

        long startNanos = System.nanoTime();
        meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", metric, "outcome", "attempt")).increment();

        long timeoutMs = Math.max(50, properties.getTimeoutMs());
        call.timeout(Duration.ofMillis(timeoutMs))
                .doOnSuccess(v -> meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", metric, "outcome", "ok")).increment())
                .doOnError(ex -> meterRegistry.counter("gateway_analytics_collect_total", Tags.of("metric", metric, "outcome", outcomeOf(ex))).increment())
                .doFinally(sig -> {
                    inflight.decrementAndGet();
                    meterRegistry.timer("gateway_analytics_collect_latency", Tags.of("metric", metric)).record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
                })
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private String outcomeOf(Throwable ex) {
        if (ex instanceof TimeoutException) {
            return "timeout";
        }
        return "error";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
