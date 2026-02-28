package com.nowcoder.community.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.gateway.config.RequestSizeLimitProperties;
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

/**
 * 请求体大小限制（基于 Content-Length 的 fast path）：
 * - 超限直接返回 HTTP 400（Result.code=400），不进入下游
 * - 主要覆盖 JSON 写接口（POST/PUT/PATCH）
 */
@Component
public class RequestSizeLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RequestSizeLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitGlobalFilter(RequestSizeLimitProperties properties, ObjectMapper objectMapper) {
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

        long maxBytes = Math.max(1, properties.getMaxBytes());
        long contentLength = request.getHeaders().getContentLength(); // -1 if unknown

        if (contentLength > maxBytes) {
            return reject(exchange, "请求体过大");
        }
        if (contentLength < 0 && properties.isFailClosedWhenUnknown()) {
            return reject(exchange, "请求体大小未知");
        }
        return chain.filter(exchange);
    }

    private boolean shouldCheck(HttpMethod method, String path) {
        if (method == null || !StringUtils.hasText(path)) {
            return false;
        }
        if (!HttpMethod.POST.equals(method) && !HttpMethod.PUT.equals(method) && !HttpMethod.PATCH.equals(method)) {
            return false;
        }
        return path.startsWith("/api/");
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<?> body = Result.error(CommonErrorCode.INVALID_ARGUMENT.getCode(), message);
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
        // 在限流/审计之前拦截，避免大包占用资源
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
}
