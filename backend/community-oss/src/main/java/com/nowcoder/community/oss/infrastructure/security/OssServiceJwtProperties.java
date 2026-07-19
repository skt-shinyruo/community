package com.nowcoder.community.oss.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oss.security.service-jwt")
public record OssServiceJwtProperties(String issuer, String audience, String scope) {

    public OssServiceJwtProperties {
        issuer = requireText("issuer", issuer);
        audience = requireText("audience", audience);
        scope = requireText("scope", scope);
        if (scope.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("oss.security.service-jwt.scope must be a single scope value");
        }
    }

    private static String requireText(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("oss.security.service-jwt." + property + " is required");
        }
        return value.trim();
    }
}
