package com.nowcoder.community.infra.oss;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

@Validated
@ConfigurationProperties("oss.client")
public record OssClientProperties(
        String baseUrl,
        String serviceSubject,
        String audience,
        String scope,
        Duration tokenTtl
) {

    private static final Duration MAX_TOKEN_TTL = Duration.ofMinutes(5);

    public OssClientProperties {
        baseUrl = normalizeBaseUrl(baseUrl);
        serviceSubject = normalizeTokenValue("service-subject", serviceSubject);
        audience = normalizeTokenValue("audience", audience);
        scope = normalizeTokenValue("scope", scope);
        tokenTtl = requireTokenTtl(tokenTtl);
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = requireText("base-url", value);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ex) {
            throw invalid("base-url");
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equals(scheme.toLowerCase(Locale.ROOT))
                || "https".equals(scheme.toLowerCase(Locale.ROOT)))
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw invalid("base-url");
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeTokenValue(String property, String value) {
        String normalized = requireText(property, value);
        if (normalized.chars().anyMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw invalid(property);
        }
        return normalized;
    }

    private static String requireText(String property, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("oss.client." + property + " is required");
        }
        return value.trim();
    }

    private static Duration requireTokenTtl(Duration tokenTtl) {
        if (tokenTtl == null) {
            throw new IllegalArgumentException("oss.client.token-ttl is required");
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative() || tokenTtl.compareTo(MAX_TOKEN_TTL) > 0) {
            throw new IllegalArgumentException("oss.client.token-ttl must be greater than zero and at most PT5M");
        }
        return tokenTtl;
    }

    private static IllegalArgumentException invalid(String property) {
        return new IllegalArgumentException("oss.client." + property + " is invalid");
    }
}
