package com.nowcoder.community.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class GatewaySecurityConfig {

    private final ObjectMapper objectMapper;

    public GatewaySecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties properties) {
        String secret = properties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少 JWT HMAC 密钥配置: security.jwt.hmac-secret");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT HMAC 密钥过短(建议>=32字节): security.jwt.hmac-secret");
        }

        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ReactiveJwtDecoder jwtDecoder) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        http.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        http.formLogin(ServerHttpSecurity.FormLoginSpec::disable);

        http.authorizeExchange(authorize -> authorize
                .pathMatchers(HttpMethod.OPTIONS).permitAll()
                .pathMatchers("/actuator/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .pathMatchers("/api/**").authenticated()
                .anyExchange().permitAll()
        );

        http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((exchange, ex) -> writeJson(exchange, HttpStatus.UNAUTHORIZED, Result.fail(CommonErrorCode.UNAUTHORIZED)))
                .accessDeniedHandler((exchange, ex) -> writeJson(exchange, HttpStatus.FORBIDDEN, Result.fail(CommonErrorCode.FORBIDDEN)))
        );

        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
        );

        return http.build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> collection) {
                List<String> roleStrings = collection.stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .filter(s -> !s.isBlank())
                        .toList();

                return roleStrings.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toSet());
            }
            return List.<GrantedAuthority>of();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    private Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus httpStatus, Result<Void> body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            exchange.getResponse().setStatusCode(httpStatus);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(httpStatus);
            return exchange.getResponse().setComplete();
        }
    }
}

