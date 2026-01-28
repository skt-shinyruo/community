package com.nowcoder.community.gateway.config;

// Gateway 安全配置：JWT 验签 + 授权矩阵 + 统一异常处理。
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.nowcoder.community.common.api.CommonErrorCode.INVALID_ARGUMENT;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties({
        JwtProperties.class,
        AnalyticsCollectProperties.class,
        GatewayRateLimitProperties.class,
        OriginGuardProperties.class,
        TrustedProxyProperties.class
})
public class GatewaySecurityConfig {

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        if (!StringUtils.hasText(jwtProperties.getHmacSecret())) {
            throw new BusinessException(INVALID_ARGUMENT, "GATEWAY_JWT_HMAC_SECRET 未配置");
        }
        byte[] secretBytes = jwtProperties.getHmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new BusinessException(INVALID_ARGUMENT, "GATEWAY_JWT_HMAC_SECRET 长度不足（建议 >= 32 字节）");
        }

        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveSecurityExceptionHandler securityExceptionHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/auth/activation/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/captcha/verify").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/password/reset/request", "/api/auth/password/reset/confirm").permitAll()
                        .pathMatchers("/api/moderation/**").hasAnyRole("ADMIN", "MODERATOR")
                        .pathMatchers(HttpMethod.GET, "/api/users/*").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/tags/hot", "/api/tags/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/posts/*/top", "/api/posts/*/wonderful", "/api/posts/*/delete").hasAnyRole("ADMIN", "MODERATOR")
                        .pathMatchers(HttpMethod.GET, "/api/search/posts").permitAll()
                        .pathMatchers("/api/search/internal/**").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/users/*/count").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll()
                        .pathMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MODERATOR")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
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
