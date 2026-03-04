package com.nowcoder.community.bootstrap.security;

import com.nowcoder.community.infra.security.jwt.AuthoritiesConverterFactory;
import com.nowcoder.community.infra.web.SecurityExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class CommunitySecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, SecurityExceptionHandler securityExceptionHandler) throws Exception {
        return http
                .securityMatcher("/api/**", "/files/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()

                        // Auth (public)
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/activation/*/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/captcha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/captcha/verify").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password/reset/request", "/api/auth/password/reset/confirm").permitAll()

                        // User (public reads)
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/batch-summary").permitAll()

                        // Content (public reads)
                        .requestMatchers(HttpMethod.GET, "/api/categories", "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tags/hot", "/api/tags/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/*", "/api/posts/*/comments", "/api/posts/*/comments/*/replies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/posts/*/top", "/api/posts/*/wonderful", "/api/posts/*/delete").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/api/moderation/**").hasAnyRole("ADMIN", "MODERATOR")

                        // Social (public reads)
                        .requestMatchers(HttpMethod.GET, "/api/likes/count", "/api/likes/counts", "/api/likes/users/*/count").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/follows/*/followees", "/api/follows/*/followers").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/follows/*/followees/count", "/api/follows/*/followers/count").permitAll()

                        // Search (public)
                        .requestMatchers(HttpMethod.GET, "/api/search/posts").permitAll()

                        // Analytics (admin/moderator)
                        .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "MODERATOR")

                        // Ops (admin-only)
                        .requestMatchers("/api/ops/**").hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(AuthoritiesConverterFactory.jwtAuthenticationConverter()))
                )
                .build();
    }
}
