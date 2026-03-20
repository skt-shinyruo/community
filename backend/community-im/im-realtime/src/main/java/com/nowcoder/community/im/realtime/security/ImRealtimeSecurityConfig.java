package com.nowcoder.community.im.realtime.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import java.util.Locale;

@Configuration
@EnableWebFluxSecurity
public class ImRealtimeSecurityConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.hmac-secret}") String secret) {
        SecretKey key = JwtSecretSupport.hmacSha256KeyOrThrow(secret);
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    public SecurityWebFilterChain webFluxSecurityFilterChain(
            ServerHttpSecurity http,
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
        // WebSocket auth is handled at message-level (first 'auth' frame).
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers(wsPathValue).permitAll()
                        // Safer default: new HTTP endpoints must be explicitly allowed.
                        .anyExchange().denyAll()
                )
                .build();
    }

    private static String normalizeEdgeMode(String edgeMode) {
        String candidate = edgeMode == null ? "" : edgeMode.trim().toLowerCase(Locale.ROOT);
        return switch (candidate) {
            case "", "direct-public-edge", "direct-public", "public-edge", "public" -> "direct-public-edge";
            case "internal-worker", "worker" -> "internal-worker";
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
