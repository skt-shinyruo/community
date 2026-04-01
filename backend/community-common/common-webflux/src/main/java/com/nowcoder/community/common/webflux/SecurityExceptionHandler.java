package com.nowcoder.community.common.webflux;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.security.response.SecurityResponseSupport;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.common.web.Result;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

public class SecurityExceptionHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, org.springframework.security.core.AuthenticationException ex) {
        return write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                SecurityResponseSupport.unauthorized(resolveTraceId(exchange), exchange.getResponse().getHeaders()::set)
        );
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return write(
                exchange,
                HttpStatus.FORBIDDEN,
                SecurityResponseSupport.forbidden(resolveTraceId(exchange), exchange.getResponse().getHeaders()::set)
        );
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        return SecurityResponseSupport.resolveTraceId(
                null,
                exchange == null ? null : exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACE_ID),
                exchange == null ? null : exchange.getRequest().getHeaders().getFirst(TraceHeaders.HEADER_TRACEPARENT)
        );
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Result<?> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            bytes = "{\"code\":500,\"message\":\"serialization failure\",\"httpStatus\":500}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
