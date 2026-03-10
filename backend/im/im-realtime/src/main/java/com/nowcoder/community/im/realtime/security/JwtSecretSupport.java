package com.nowcoder.community.im.realtime.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class JwtSecretSupport {

    private JwtSecretSupport() {
    }

    public static SecretKey hmacSha256KeyOrThrow(String raw) {
        String secret = raw == null ? "" : raw.trim();
        if (secret.length() < 32) {
            throw new IllegalArgumentException("JWT_HMAC_SECRET too short (need >= 32 chars)");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}

