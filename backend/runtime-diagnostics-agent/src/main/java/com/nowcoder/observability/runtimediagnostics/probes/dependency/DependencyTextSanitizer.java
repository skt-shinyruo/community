package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DependencyTextSanitizer {

    private DependencyTextSanitizer() {
    }

    public static String hash16(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", digest[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static String safeToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return sanitized.isBlank() ? fallback : sanitized;
    }
}
