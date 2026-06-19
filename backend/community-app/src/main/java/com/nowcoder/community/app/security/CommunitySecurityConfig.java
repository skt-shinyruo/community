package com.nowcoder.community.app.security;

import com.nowcoder.community.auth.infrastructure.web.TokenFreshnessFilter;
import com.nowcoder.community.infra.security.jwt.AuthoritiesConverterFactory;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.util.List;

@Configuration
public class CommunitySecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            List<ApiSecurityRules> securityRules,
            TokenFreshnessFilter tokenFreshnessFilter
    ) throws Exception {
        return http
                .securityMatcher("/api/**", "/internal/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/internal/im/realtime/projections/**").hasAuthority("SCOPE_im.realtime.internal")
                )
                .authorizeHttpRequests(auth -> {
                    for (ApiSecurityRules rules : securityRules) {
                        rules.apply(auth);
                    }
                    auth
                        .anyRequest().authenticated()
                    ;
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(AuthoritiesConverterFactory.jwtAuthenticationConverter()))
                )
                .addFilterAfter(tokenFreshnessFilter, BearerTokenAuthenticationFilter.class)
                .build();
    }
}
