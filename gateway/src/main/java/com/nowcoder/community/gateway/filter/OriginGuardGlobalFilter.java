package com.nowcoder.community.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.gateway.config.OriginGuardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OriginGuardGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(OriginGuardGlobalFilter.class);

    private final OriginGuardProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean warnedEmptyAllowlist = new AtomicBoolean(false);

    public OriginGuardGlobalFilter(OriginGuardProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (properties == null || !properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getURI().getPath();

        if (!shouldCheck(method, path)) {
            return chain.filter(exchange);
        }

        String origin = request.getHeaders().getOrigin();
        if (!StringUtils.hasText(origin)) {
            // 与旧 auth-service 行为保持一致：无 Origin 头直接放行（兼容非浏览器客户端）
            return chain.filter(exchange);
        }

        List<String> allowed = properties.getAllowedOrigins();
        if (allowed == null || allowed.isEmpty()) {
            // fail-open：避免误配置导致全站不可用
            if (warnedEmptyAllowlist.compareAndSet(false, true)) {
                log.warn("[origin-guard] allowed-origins 为空，已退化为 fail-open（建议在配置中心补齐 allowlist）");
            }
            return chain.filter(exchange);
        }

        if (allowed.contains(origin)) {
            return chain.filter(exchange);
        }

        return forbidden(exchange, "Origin 不被允许");
    }

    private boolean shouldCheck(HttpMethod method, String path) {
        if (method == null || path == null) {
            return false;
        }
        if (!HttpMethod.POST.equals(method)) {
            return false;
        }
        // 仅覆盖 cookie 会话相关敏感入口
        return "/api/auth/login".equals(path)
                || "/api/auth/refresh".equals(path)
                || "/api/auth/logout".equals(path);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<?> body = Result.error(CommonErrorCode.FORBIDDEN.getCode(), message);
        // gateway 侧 Result 的 traceId 来自 TraceIdGlobalFilter 注入的 header（避免 ThreadLocal 在 reactive 下为空）
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIdGlobalFilter.HEADER_TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            body.setTraceId(traceId);
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 仅次于 trace 注入，尽早拦截，避免占用限流/审计等后续链路
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
