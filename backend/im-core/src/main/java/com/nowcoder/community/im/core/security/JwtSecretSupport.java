package com.nowcoder.community.im.core.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class JwtSecretSupport {

    private JwtSecretSupport() {
    }

    public static SecretKey hmacSha256KeyOrThrow(String secret) {
        String s = secret == null ? "" : secret.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("security.jwt.hmac-secret is required");
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.hmac-secret must be >= 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}

