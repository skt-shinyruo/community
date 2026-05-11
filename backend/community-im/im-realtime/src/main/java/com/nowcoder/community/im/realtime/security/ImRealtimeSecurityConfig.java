package com.nowcoder.community.im.realtime.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import java.util.Locale;

@Configuration
@EnableWebFluxSecurity
public class ImRealtimeSecurityConfig {

    @Bean
    public SecurityWebFilterChain webFluxSecurityFilterChain(
            ServerHttpSecurity http,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler,
            @Value("${im.ws.path:/ws/im}") String wsPath,
            @Value("${im.edge.mode:direct-public-edge}") String edgeMode,
            @Value("${im.edge.direct-public.ws-path:/ws/im}") String directPublicWsPath,
            @Value("${im.edge.internal-worker.ws-path:/internal/ws/im}") String internalWorkerWsPath
    ) {
        String normalizedEdgeMode = normalizeEdgeMode(edgeMode);
        String wsPathValue = normalizeWsPath(wsPath);
        validateModeCompatiblePath(
                normalizedEdgeMode,
                wsPathValue,
                normalizeWsPath(directPublicWsPath),
                normalizeWsPath(internalWorkerWsPath)
        );
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

    private static String normalizeEdgeMode(String edgeMode) {
        String candidate = edgeMode == null ? "" : edgeMode.trim().toLowerCase(Locale.ROOT);
        return switch (candidate) {
            case "direct-public-edge" -> "direct-public-edge";
            case "internal-worker" -> "internal-worker";
            default -> throw new IllegalArgumentException("Unsupported im.edge.mode: " + edgeMode);
        };
    }

    private static String normalizeWsPath(String wsPath) {
        if (wsPath == null || wsPath.isBlank()) {
            return "/ws/im";
        }
        return wsPath.startsWith("/") ? wsPath : "/" + wsPath;
    }

    private static void validateModeCompatiblePath(
            String edgeMode,
            String actualWsPath,
            String directPublicWsPath,
            String internalWorkerWsPath
    ) {
        if ("internal-worker".equals(edgeMode)
                && actualWsPath.equals(directPublicWsPath)
                && !actualWsPath.equals(internalWorkerWsPath)) {
            throw new IllegalStateException(
                    "im.edge.mode=internal-worker cannot expose the direct-public websocket path"
            );
        }
        if ("direct-public-edge".equals(edgeMode)
                && actualWsPath.equals(internalWorkerWsPath)
                && !actualWsPath.equals(directPublicWsPath)) {
            throw new IllegalStateException(
                    "im.edge.mode=direct-public-edge cannot expose the internal-worker websocket path"
            );
        }
    }
}
