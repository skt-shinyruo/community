package com.nowcoder.community.oss.infrastructure.security;

import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.common.security.jwt.JwtSubjects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableConfigurationProperties(OssServiceJwtProperties.class)
public class OssSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain ossInternalSecurityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            JwtProperties jwtProperties,
            OssServiceJwtProperties serviceJwtProperties
    ) throws Exception {
        NimbusJwtDecoder serviceJwtDecoder = serviceJwtDecoder(jwtProperties, serviceJwtProperties);
        return http
                .securityMatcher("/internal/oss/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                isAuthorizedService(authentication.get(), serviceJwtProperties)
                        ))
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(serviceJwtDecoder))
                )
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain ossUserSecurityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            JwtDecoder jwtDecoder,
            OssServiceJwtProperties serviceJwtProperties
    ) throws Exception {
        return http
                .securityMatcher("/api/**", "/files/**", "/actuator/health", "/actuator/info")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                isAuthorizedUser(authentication.get(), serviceJwtProperties.audience())
                        ))
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                )
                .build();
    }

    private NimbusJwtDecoder serviceJwtDecoder(
            JwtProperties jwtProperties,
            OssServiceJwtProperties serviceJwtProperties
    ) {
        NimbusJwtDecoder decoder = JwtCodecs.jwtDecoder(jwtProperties);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(serviceJwtProperties.issuer()),
                audienceValidator(serviceJwtProperties.audience())
        ));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(expectedAudience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "The required audience is missing",
                        null
                ));
    }

    private boolean isAuthorizedService(Authentication authentication, OssServiceJwtProperties properties) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return false;
        }
        String subject = jwtAuthentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            return false;
        }
        String expectedAuthority = "SCOPE_" + properties.scope();
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }

    private boolean isAuthorizedUser(Authentication authentication, String serviceAudience) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()
                || JwtSubjects.tryUserUuid(jwtAuthentication.getToken()) == null) {
            return false;
        }
        return jwtAuthentication.getToken().getAudience() == null
                || !jwtAuthentication.getToken().getAudience().contains(serviceAudience);
    }
}
