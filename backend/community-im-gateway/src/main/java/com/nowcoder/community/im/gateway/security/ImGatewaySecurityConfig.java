package com.nowcoder.community.im.gateway.security;

import com.nowcoder.community.common.security.jwt.JwtProperties;
import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import com.nowcoder.community.im.ticket.ImSessionTicketProperties;
import com.nowcoder.community.im.ticket.SessionTicketCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties({ImGatewaySessionProperties.class, ImSessionTicketProperties.class})
public class ImGatewaySecurityConfig {

    @Bean
    SessionTicketCodec sessionTicketCodec(
            JwtProperties accessProperties,
            ImSessionTicketProperties ticketProperties
    ) {
        return new SessionTicketCodec(
                ticketProperties,
                ticketProperties.secretKeyOrThrow(accessProperties)
        );
    }

    @Bean
    @Order(1)
    SecurityWebFilterChain imGatewayActuatorSecurityWebFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            @Value("${community.metrics.basic-auth.password:}") String metricsPassword
    ) {
        boolean prometheusAuthConfigured = StringUtils.hasText(metricsPassword);
        ServerHttpSecurity builder = http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/actuator/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
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
    @Order(2)
    SecurityWebFilterChain imGatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            ImGatewaySessionProperties sessionProperties
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/im/sessions").permitAll()
                        .pathMatchers(sessionProperties.getWs().getPath()).permitAll()
                        .anyExchange().denyAll())
                .build();
    }

    @Bean
    ReactiveUserDetailsService prometheusUserDetailsService(
            @Value("${community.metrics.basic-auth.username:prometheus}") String username,
            @Value("${community.metrics.basic-auth.password:}") String password
    ) {
        if (!StringUtils.hasText(password)) {
            return ignoredUsername -> Mono.empty();
        }
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
}
