package com.nowcoder.community.im.realtime.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

@Configuration
@EnableWebFluxSecurity
public class ImRealtimeSecurityConfig {

    @Bean
    public SecurityWebFilterChain webFluxSecurityFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            @Value("${im.ws.path:/internal/ws/im}") String wsPath
    ) {
        String wsPathValue = normalizeWsPath(wsPath);
        // WebSocket auth is handled at message-level (first 'connect' frame).
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers(wsPathValue).permitAll()
                        // WebSocket connect auth remains inside the handler through signed session tickets.
                        .anyExchange().denyAll()
                )
                .build();
    }

    private static String normalizeWsPath(String wsPath) {
        if (wsPath == null || wsPath.isBlank()) {
            return "/internal/ws/im";
        }
        return wsPath.startsWith("/") ? wsPath : "/" + wsPath;
    }
}
