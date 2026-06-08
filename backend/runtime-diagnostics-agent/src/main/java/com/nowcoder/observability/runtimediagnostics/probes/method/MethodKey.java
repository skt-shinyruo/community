package com.nowcoder.observability.runtimediagnostics.probes.method;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

public record MethodKey(String className, String methodName, String signatureHash) {

    public static MethodKey from(String className, String methodName, String descriptor) {
        String safeClassName = sanitize(className);
        String safeMethodName = sanitize(methodName);
        String rawSignature = safeClassName + "#" + safeMethodName + ":" + sanitize(descriptor);
        return new MethodKey(safeClassName, safeMethodName, hash(rawSignature));
    }

    public static MethodKey from(Class<?> declaringClass, String methodName, Class<?> returnType, Class<?>[] parameterTypes) {
        String className = sanitize(declaringClass == null ? null : declaringClass.getName());
        String safeMethodName = sanitize(methodName);
        String parameters = parameterTypes == null ? "" : Stream.of(parameterTypes)
                .map(Class::getName)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String rawSignature = className + "#" + safeMethodName + "(" + parameters + "):" + (returnType == null ? "void" : returnType.getName());
        return new MethodKey(className, safeMethodName, hash(rawSignature));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private static String hash(String rawSignature) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawSignature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toUnsignedString(rawSignature.hashCode(), 16);
        }
    }
}
