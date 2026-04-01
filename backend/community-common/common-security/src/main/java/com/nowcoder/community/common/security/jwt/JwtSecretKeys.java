package com.nowcoder.community.common.security.jwt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class JwtSecretKeys {

    private JwtSecretKeys() {
    }

    public static SecretKey hmacSha256OrThrow(JwtProperties properties) {
        String secret = properties == null ? null : properties.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("security.jwt.hmac-secret is required");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.hmac-secret must be >= 32 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
