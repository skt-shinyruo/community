package com.nowcoder.community.gateway.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCorsProperties.class)
public class GatewayCorsConfig {

    @Bean
    CorsWebFilter gatewayCorsWebFilter(GatewayCorsProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", buildCorsConfiguration(properties));
        source.registerCorsConfiguration("/files/**", buildCorsConfiguration(properties));
        source.registerCorsConfiguration("/ws/**", buildCorsConfiguration(properties));
        return new CorsWebFilter(source);
    }

    public static CorsConfiguration buildCorsConfiguration(GatewayCorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        if (properties != null && !CollectionUtils.isEmpty(properties.getAllowedOrigins())) {
            config.setAllowedOrigins(properties.getAllowedOrigins());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        return config;
    }
}
