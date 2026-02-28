package com.nowcoder.community.gateway.config;

// Gateway 安全配置：最小边界护栏 + ops 入口双保险 + 统一异常处理。
import com.nowcoder.community.infra.security.jwt.AuthoritiesConverterFactory;
import com.nowcoder.community.platform.web.reactive.ReactiveSecurityExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({
        AnalyticsCollectProperties.class,
        GatewayRateLimitProperties.class,
        GatewayAuditProperties.class,
        RequestSizeLimitProperties.class
})
public class GatewaySecurityConfig {

    @Bean
    @Order(2)
    public SecurityWebFilterChain internalDenyWebFilterChain(ServerHttpSecurity http, ReactiveSecurityExceptionHandler securityExceptionHandler) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeExchange(ex -> ex.anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityWebFilterChain opsSecurityWebFilterChain(ServerHttpSecurity http, ReactiveSecurityExceptionHandler securityExceptionHandler) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/ops/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .anyExchange().hasRole("ADMIN")
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    @Order(100)
    public SecurityWebFilterChain transparentAllowAllWebFilterChain(ServerHttpSecurity http) {
        // 透明模式：gateway 不再维护业务路径级权限矩阵（SSOT 下沉到各服务）。
        // 目的：降低 gateway 发布频率与误配爆炸半径。
        return http
                .securityMatcher(ServerWebExchangeMatchers.anyExchange())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = AuthoritiesConverterFactory.jwtAuthenticationConverter();
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
