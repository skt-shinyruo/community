package com.nowcoder.community.gateway.config;

// Gateway 安全配置：JWT 验签 + 授权矩阵 + 统一异常处理。
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({
        AnalyticsCollectProperties.class,
        GatewayRateLimitProperties.class,
        GatewayAuditProperties.class,
        OriginGuardProperties.class,
        TrustedProxyProperties.class,
        RequestSizeLimitProperties.class
})
public class GatewaySecurityConfig {

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "gateway.security", name = "mode", havingValue = "legacy-matrix")
    public SecurityWebFilterChain legacyMatrixSecurityWebFilterChain(ServerHttpSecurity http, ReactiveSecurityExceptionHandler securityExceptionHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // internal 接口仅用于服务间调用：网关显式拒绝，避免误配路由导致对外暴露。
                        .pathMatchers("/internal/**").denyAll()
                        .pathMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/auth/activation/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/captcha/verify").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/password/reset/request", "/api/auth/password/reset/confirm").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/files/**").permitAll()
	                        .pathMatchers("/api/moderation/**").hasAnyRole("ADMIN", "MODERATOR")
	                        // 对外运维入口：在网关侧先做角色收敛；下游 internal 入口不做 header token 鉴权，因此必须确保 internal 端口仅内网可达。
	                        .pathMatchers("/api/ops/**").hasRole("ADMIN")
	                        .pathMatchers("/api/users/admin/**").hasRole("ADMIN")
	                        .pathMatchers(HttpMethod.GET, "/api/users/*").permitAll()
	                        .pathMatchers(HttpMethod.POST, "/api/users/batch-summary").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/tags/hot", "/api/tags/**").permitAll()
	                        .pathMatchers(HttpMethod.POST, "/api/posts/*/top", "/api/posts/*/wonderful", "/api/posts/*/delete").hasAnyRole("ADMIN", "MODERATOR")
	                        // 仅开放帖子读接口；显式列出公开路径，避免使用 /** 导致未来新增的 GET 保护接口被误放行。
	                        .pathMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments", "/api/posts/*/comments/*/replies").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/search/posts").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/counts", "/api/likes/users/*/count").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll()
	                        .pathMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll()
                        .pathMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MODERATOR")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(prefix = "gateway.security", name = "mode", havingValue = "transparent", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "gateway.security", name = "mode", havingValue = "transparent", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "gateway.security", name = "mode", havingValue = "transparent", matchIfMissing = true)
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
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> authorities = jwt.getClaimAsStringList("authorities");
            if (authorities == null) {
                return List.of();
            }
            return authorities.stream()
                    .filter(StringUtils::hasText)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
