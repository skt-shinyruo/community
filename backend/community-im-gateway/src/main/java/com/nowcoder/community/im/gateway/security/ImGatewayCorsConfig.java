package com.nowcoder.community.im.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImGatewayCorsProperties.class)
public class ImGatewayCorsConfig {

    @Bean
    WebFilter imGatewayCorsWebFilter(ImGatewayCorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", buildCorsConfiguration(properties));
        CorsWebFilter delegate = new CorsWebFilter(source);
        return (exchange, chain) -> isWebSocketUpgrade(exchange)
                ? chain.filter(exchange)
                : delegate.filter(exchange, chain);
    }

    public static CorsConfiguration buildCorsConfiguration(ImGatewayCorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        if (properties != null && !CollectionUtils.isEmpty(properties.getAllowedOrigins())) {
            config.setAllowedOrigins(properties.getAllowedOrigins());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("traceparent"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }

    private static boolean isWebSocketUpgrade(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return false;
        }
        String upgrade = exchange.getRequest().getHeaders().getUpgrade();
        return upgrade != null && "websocket".equalsIgnoreCase(upgrade.trim());
    }
}
