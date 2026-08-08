package com.nowcoder.community.auth.config;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

public final class PasswordResetUrlPolicy {

    private PasswordResetUrlPolicy() {
    }

    public static String normalizeHttpsBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("password reset base URL is required");
        }

        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("password reset base URL is invalid", exception);
        }
        int port = uri.getPort();
        String rawAuthority = uri.getRawAuthority();
        if (!uri.isAbsolute()
                || uri.isOpaque()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !StringUtils.hasText(uri.getHost())
                || (rawAuthority != null && rawAuthority.endsWith(":"))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (port != -1 && (port < 1 || port > 65_535))) {
            throw new IllegalArgumentException("password reset base URL must be an absolute HTTPS URL");
        }

        String normalized = uri.normalize().toASCIIString();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
