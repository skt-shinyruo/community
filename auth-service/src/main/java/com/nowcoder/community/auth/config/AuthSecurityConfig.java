package com.nowcoder.community.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.api.CommonErrorCode;
import com.nowcoder.community.common.api.Result;
import com.nowcoder.community.common.trace.TraceId;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthSecurityConfig {

    private final ObjectMapper objectMapper;

    public AuthSecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        String secret = properties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少 JWT HMAC 密钥配置: security.jwt.hmac-secret");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT HMAC 密钥过短(建议>=32字节): security.jwt.hmac-secret");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                .anyRequest().authenticated()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
        );

        http.exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint((request, response, authException) -> writeJson(response, HttpStatus.UNAUTHORIZED, Result.fail(CommonErrorCode.UNAUTHORIZED)))
                .accessDeniedHandler((request, response, accessDeniedException) -> writeJson(response, HttpStatus.FORBIDDEN, Result.fail(CommonErrorCode.FORBIDDEN)))
        );

        http.httpBasic(basic -> basic.disable());
        http.formLogin(form -> form.disable());

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
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
        return converter;
    }

    private void writeJson(HttpServletResponse response, HttpStatus httpStatus, Result<Void> body) {
        response.setStatus(httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String traceId = TraceId.currentOrNull();
        if (traceId != null && !traceId.isBlank()) {
            response.setHeader(TraceId.HEADER_NAME, traceId);
        }
        try {
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {
        }
    }
}
