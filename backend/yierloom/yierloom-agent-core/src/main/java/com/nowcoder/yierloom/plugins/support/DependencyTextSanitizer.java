package com.nowcoder.yierloom.plugins.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class DependencyTextSanitizer {
    private static final int MAX_DIMENSION_LENGTH = 512;

    private DependencyTextSanitizer() {
    }

    public static String hash16(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public static String dimension(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        int length = safeEndIndex(value, MAX_DIMENSION_LENGTH);
        StringBuilder sanitized = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        return sanitized.toString();
    }

    private static int safeEndIndex(String value, int maximum) {
        int end = Math.min(value.length(), maximum);
        if (end > 0
                && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            return end - 1;
        }
        return end;
    }
}
