package com.nowcoder.community.gateway.config;

// Reactive 安全异常处理：确保 401/403 响应体携带 traceId。
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.gateway.filter.TraceIdSupport;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
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
}
