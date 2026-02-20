package com.nowcoder.community.infra.security.autoconfig;

import com.nowcoder.community.infra.security.jwt.JwtProperties;
import com.nowcoder.community.infra.security.jwt.JwtSecretKeys;
import com.nowcoder.community.infra.security.metrics.MetricsBasicAuthProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({ServerHttpSecurity.class, SecurityWebFilterChain.class})
public class ReactiveSecurityInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ReactiveUserDetailsService prometheusUserDetailsService(MetricsBasicAuthProperties properties) {
        String u = properties == null ? null : properties.getUsername();
        String p = properties == null ? null : properties.getPassword();
        String username = u == null || u.isBlank() ? "prometheus" : u.trim();
        String password = p == null || p.isBlank() ? "dev-prometheus-pass" : p;
        UserDetails user = User.withUsername(username)
                .password("{noop}" + password)
                .roles("PROMETHEUS")
                .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        return NimbusReactiveJwtDecoder.withSecretKey(JwtSecretKeys.hmacSha256OrThrow(jwtProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    @Order(1)
    @ConditionalOnMissingBean(name = "actuatorSecurityWebFilterChain")
    public SecurityWebFilterChain actuatorSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/actuator/prometheus").hasRole("PROMETHEUS")
                        .anyExchange().denyAll()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}

