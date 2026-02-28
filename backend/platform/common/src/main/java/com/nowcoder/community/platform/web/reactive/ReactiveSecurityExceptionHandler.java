package com.nowcoder.community.platform.web.reactive;

// Reactive 安全异常处理：确保 401/403 响应体携带 traceId。
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.contracts.api.CommonErrorCode;
import com.nowcoder.community.contracts.api.Result;
import com.nowcoder.community.contracts.trace.TraceHeaders;
import com.nowcoder.community.contracts.trace.TraceIdCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WebFlux 安全异常处理：
 * - 401/403 返回统一 {@link Result} 协议体
 * - 回填 traceId，方便网关/调用方快速定位
 */
public class ReactiveSecurityExceptionHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ReactiveSecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return write(exchange, HttpStatus.UNAUTHORIZED, Result.error(CommonErrorCode.UNAUTHORIZED));
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(exchange, HttpStatus.FORBIDDEN, Result.error(CommonErrorCode.FORBIDDEN));
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Result<?> body) {
        if (exchange == null || exchange.getResponse() == null || exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpHeaders headers = exchange.getRequest() == null ? null : exchange.getRequest().getHeaders();
        String traceId = TraceIdCodec.resolveTraceId(
                headers == null ? null : headers.getFirst(TraceHeaders.HEADER_TRACE_ID),
                headers == null ? null : headers.getFirst(TraceHeaders.HEADER_TRACEPARENT)
        );
        body.setTraceId(traceId);
        exchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACE_ID, traceId);
        exchange.getResponse().getHeaders().set(TraceHeaders.HEADER_TRACEPARENT, TraceIdCodec.buildTraceparent(traceId));

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            return exchange.getResponse().setComplete();
        }
    }
}

