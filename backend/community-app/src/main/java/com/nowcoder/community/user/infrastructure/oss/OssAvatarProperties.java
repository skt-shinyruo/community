package com.nowcoder.community.user.infrastructure.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oss.avatar")
public record OssAvatarProperties(String publicBaseUrl) {

    public OssAvatarProperties {
        publicBaseUrl = normalize(publicBaseUrl);
    }

    private static String normalize(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:12880" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
