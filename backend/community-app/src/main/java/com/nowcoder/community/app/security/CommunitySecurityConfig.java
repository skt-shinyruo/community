package com.nowcoder.community.app.security;

import com.nowcoder.community.auth.infrastructure.web.TokenFreshnessFilter;
import com.nowcoder.community.common.security.jwt.JwtCodecs;
import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.jwt.AuthoritiesConverterFactory;
import com.nowcoder.community.common.web.SecurityExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
    public FilterRegistrationBean<TokenFreshnessFilter> tokenFreshnessFilterRegistration(
            ObjectProvider<TokenFreshnessFilter> filterProvider
    ) {
        FilterRegistrationBean<TokenFreshnessFilter> registration = new FilterRegistrationBean<>();
        TokenFreshnessFilter filter = filterProvider.getIfAvailable();
        if (filter != null) {
            registration.setFilter(filter);
        }
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            ObjectProvider<JwtProperties> jwtPropertiesProvider
    ) throws Exception {
        var chain = http
                .securityMatcher("/internal/**")
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/internal/im/realtime/projections/**")
                        .hasAuthority("SCOPE_im.realtime.internal")
                        .anyRequest().denyAll()
                );
        JwtProperties jwtProperties = jwtPropertiesProvider.getIfAvailable();
        if (jwtProperties != null) {
            chain.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(
                    JwtCodecs.serviceTokenDecoder(jwtProperties, JwtCodecs.resolvedIssuer(jwtProperties), "community-app")
            )));
        }
        return chain.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityExceptionHandler securityExceptionHandler,
            List<ApiSecurityRules> securityRules,
            ObjectProvider<TokenFreshnessFilter> tokenFreshnessFilter
    ) throws Exception {
        var chain = http
                .securityMatcher("/api/**")
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
                ;
        TokenFreshnessFilter freshnessFilter = tokenFreshnessFilter.getIfAvailable();
        if (freshnessFilter != null) {
            chain.addFilterAfter(freshnessFilter, BearerTokenAuthenticationFilter.class);
        }
        return chain.build();
    }
}
