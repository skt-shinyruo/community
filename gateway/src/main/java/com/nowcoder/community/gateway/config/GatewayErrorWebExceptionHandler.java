package com.nowcoder.community.gateway.config;

// 网关全局异常收敛：将 WebFlux/Gateway 内部异常统一映射为 Result + 4xx/5xx，并回填 traceId。
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.GatewayErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.gateway.filter.TraceIdSupport;
import io.netty.handler.timeout.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * 说明：
 * - 只处理“网关自身抛出的异常”（路由、解析、上游不可用等），不干预下游服务返回的非 2xx 响应体。
 * - 安全异常（401/403）优先由 {@link ReactiveSecurityExceptionHandler} 处理；若仍抛出到此处，也会按统一协议返回。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GatewayErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange == null || exchange.getResponse() == null || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        String traceId = TraceIdSupport.resolveTraceId(exchange.getRequest() == null ? null : exchange.getRequest().getHeaders());

        MappedError mapped = map(ex);
        HttpStatus status = mapped.status();

        // 仅对 5xx 记录 warn，避免 4xx 噪音影响排障；traceId 由响应回填给调用方。
        if (status.is5xxServerError()) {
            log.warn("[gateway-error] status={} traceId={} err={}", status.value(), traceId, ex == null ? "null" : ex.toString());
        }

        Result<Void> body = Result.error(mapped.code(), mapped.message());
        body.setTraceId(traceId);

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TraceIdSupport.HEADER_TRACEPARENT, TraceIdSupport.buildTraceparent(traceId));

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }

    private MappedError map(Throwable ex) {
        if (ex instanceof ServerWebInputException) {
            return new MappedError(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT.getCode(), CommonErrorCode.INVALID_ARGUMENT.getMessage());
        }
        if (ex instanceof TimeoutException || ex instanceof java.util.concurrent.TimeoutException) {
            return new MappedError(HttpStatus.GATEWAY_TIMEOUT, GatewayErrorCode.GATEWAY_TIMEOUT.getCode(), GatewayErrorCode.GATEWAY_TIMEOUT.getMessage());
        }
        if (ex instanceof IllegalArgumentException) {
            return new MappedError(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_ARGUMENT.getCode(), CommonErrorCode.INVALID_ARGUMENT.getMessage());
        }

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = httpStatusOf(rse.getStatusCode());
            return mapByStatus(status, rse.getReason());
        }

        return new MappedError(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage());
    }

    private MappedError mapByStatus(HttpStatus status, String reason) {
        if (status == null) {
            return new MappedError(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage());
        }

        return switch (status.value()) {
            case 400 -> new MappedError(status, CommonErrorCode.INVALID_ARGUMENT.getCode(), messageOrDefault(reason, CommonErrorCode.INVALID_ARGUMENT.getMessage()));
            case 401 -> new MappedError(status, CommonErrorCode.UNAUTHORIZED.getCode(), messageOrDefault(reason, CommonErrorCode.UNAUTHORIZED.getMessage()));
            case 403 -> new MappedError(status, CommonErrorCode.FORBIDDEN.getCode(), messageOrDefault(reason, CommonErrorCode.FORBIDDEN.getMessage()));
            case 404 -> new MappedError(status, GatewayErrorCode.ROUTE_NOT_FOUND.getCode(), GatewayErrorCode.ROUTE_NOT_FOUND.getMessage());
            case 413 -> new MappedError(status, GatewayErrorCode.REQUEST_TOO_LARGE.getCode(), GatewayErrorCode.REQUEST_TOO_LARGE.getMessage());
            case 429 -> new MappedError(status, CommonErrorCode.TOO_MANY_REQUESTS.getCode(), CommonErrorCode.TOO_MANY_REQUESTS.getMessage());
            case 502 -> new MappedError(status, GatewayErrorCode.BAD_GATEWAY.getCode(), GatewayErrorCode.BAD_GATEWAY.getMessage());
            case 503 -> new MappedError(status, GatewayErrorCode.UPSTREAM_UNAVAILABLE.getCode(), GatewayErrorCode.UPSTREAM_UNAVAILABLE.getMessage());
            case 504 -> new MappedError(status, GatewayErrorCode.GATEWAY_TIMEOUT.getCode(), GatewayErrorCode.GATEWAY_TIMEOUT.getMessage());
            default -> status.is4xxClientError()
                    ? new MappedError(status, status.value(), messageOrDefault(reason, status.getReasonPhrase()))
                    : new MappedError(status, CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage());
        };
    }

    private HttpStatus httpStatusOf(HttpStatusCode code) {
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        try {
            return HttpStatus.valueOf(code.value());
        } catch (IllegalArgumentException ignored) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String messageOrDefault(String candidate, String fallback) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return fallback;
    }

    private record MappedError(HttpStatus status, int code, String message) {
    }
}
