package com.nowcoder.community.app.security;

import com.nowcoder.community.infra.security.origin.OriginGuardProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CommunityCorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(OriginGuardProperties originGuardProperties) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = originGuardProperties == null ? List.of() : originGuardProperties.getAllowedOrigins();
        if (!CollectionUtils.isEmpty(allowedOrigins)) {
            config.setAllowedOrigins(allowedOrigins);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        source.registerCorsConfiguration("/files/**", config);
        return source;
    }
}
