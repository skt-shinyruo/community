package com.nowcoder.community.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    @Order(1)
    SecurityWebFilterChain gatewayActuatorSecurityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${community.metrics.basic-auth.password:}") String metricsPassword
    ) {
        boolean prometheusAuthConfigured = StringUtils.hasText(metricsPassword);
        ServerHttpSecurity builder = http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> {
                    exchanges.pathMatchers(HttpMethod.OPTIONS).permitAll();
                    exchanges.pathMatchers("/actuator/health", "/actuator/info").permitAll();
                    if (prometheusAuthConfigured) {
                        exchanges.pathMatchers("/actuator/prometheus").hasRole("PROMETHEUS");
                    } else {
                        exchanges.pathMatchers("/actuator/prometheus").denyAll();
                    }
                    exchanges.anyExchange().denyAll();
                });
        if (prometheusAuthConfigured) {
            builder.httpBasic(Customizer.withDefaults());
        } else {
            builder.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "community.metrics.basic-auth", name = "password")
    MapReactiveUserDetailsService prometheusUserDetailsService(
            @Value("${community.metrics.basic-auth.username:prometheus}") String username,
            @Value("${community.metrics.basic-auth.password}") String password
    ) {
        String resolvedUsername = StringUtils.hasText(username) ? username.trim() : "prometheus";
        String resolvedPassword = password.trim();
        if (resolvedPassword.length() < 12) {
            throw new IllegalArgumentException("community.metrics.basic-auth.password length must be at least 12");
        }
        UserDetails prometheusUser = User.withUsername(resolvedUsername)
                .password("{noop}" + resolvedPassword)
                .roles("PROMETHEUS")
                .build();
        return new MapReactiveUserDetailsService(prometheusUser);
    }

    @Bean
    @Order(2)
    SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // Gateway is only the edge/router. AuthN/AuthZ remains with upstream owners.
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }
}
