package com.nowcoder.yierloom.plugins.method;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record MethodKey(String className, String methodName, String signatureHash) {
    private static final int MAX_COMPONENT_LENGTH = 240;

    public static MethodKey from(String className, String methodName, String descriptor) {
        String safeClassName = sanitize(className);
        String safeMethodName = sanitize(methodName);
        String signature = safeClassName + "#" + safeMethodName + ":" + sanitize(descriptor);
        return new MethodKey(safeClassName, safeMethodName, hash(signature));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= MAX_COMPONENT_LENGTH
                ? value
                : value.substring(0, MAX_COMPONENT_LENGTH);
    }

    private static String hash(String signature) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
