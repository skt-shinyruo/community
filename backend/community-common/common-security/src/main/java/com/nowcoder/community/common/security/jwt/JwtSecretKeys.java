package com.nowcoder.community.common.security.jwt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class JwtSecretKeys {

    private static final Set<String> PLACEHOLDER_JWT_SECRETS = Set.of(
            "dev-secret-please-change-at-least-32bytes",
            "dev-jwt-hmac-secret-please-change-me-123456"
    );

    private JwtSecretKeys() {
    }

    public static SecretKey hmacSha256OrThrow(JwtProperties properties) {
        String secret = properties == null ? null : properties.getHmacSecret();
        secret = secret == null ? null : secret.trim();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("security.jwt.hmac-secret is required");
        }
        if (PLACEHOLDER_JWT_SECRETS.contains(secret)) {
            throw new IllegalArgumentException(
                    "security.jwt.hmac-secret must not use a known placeholder; set JWT_HMAC_SECRET to a unique value >= 32 bytes"
            );
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.hmac-secret must be >= 32 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
