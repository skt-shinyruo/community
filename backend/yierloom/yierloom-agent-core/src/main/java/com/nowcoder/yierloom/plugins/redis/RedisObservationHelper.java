package com.nowcoder.yierloom.plugins.redis;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class RedisObservationHelper {
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private RedisObservationHelper() {
    }

    public static void observe(
            String methodName,
            Object[] arguments,
            long durationMs,
            boolean error
    ) {
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            YierLoomBridge.observe(
                    "redis", describe(methodName, arguments, durationMs, error));
        } finally {
            OBSERVING.remove();
        }
    }

    public static PluginObservation describe(
            String methodName,
            Object[] arguments,
            long durationMs,
            boolean error
    ) {
        PluginObservation.Builder observation = PluginObservation.builder("dependency-call")
                .attribute("redis.command", normalizeCommand(methodName))
                .attribute("redis.namespace.hash", hash16(namespace(arguments)))
                .longField("duration.ms", Math.max(0, durationMs))
                .booleanField("error", error);
        addTraceFields(observation, currentTraceFields());
        return observation.build();
    }

    private static String normalizeCommand(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return "UNKNOWN";
        }
        StringBuilder normalized = new StringBuilder(methodName.length());
        for (int index = 0; index < methodName.length(); index++) {
            char character = methodName.charAt(index);
            normalized.append(isCommandCharacter(character) ? character : '_');
        }
        return normalized.toString().toUpperCase(Locale.ROOT);
    }

    private static boolean isCommandCharacter(char character) {
        return character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_';
    }

    private static String namespace(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof String value && !value.isBlank()) {
                int separator = value.indexOf(':');
                return separator > 0 ? value.substring(0, separator) : value;
            }
        }
        return null;
    }

    private static String hash16(String value) {
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

    private static void addTraceFields(PluginObservation.Builder observation, String[] fields) {
        if (fields[0] != null && !fields[0].isBlank()) {
            observation.attribute("trace.id", fields[0]);
        }
        if (fields[1] != null && !fields[1].isBlank()) {
            observation.attribute("span.id", fields[1]);
        }
    }

    private static String[] currentTraceFields() {
        String[] fields = readOpenTelemetry();
        return fields[0] == null ? readMdc() : fields;
    }

    private static String[] readOpenTelemetry() {
        try {
            Class<?> spanClass = loadClass("io.opentelemetry.api.trace.Span");
            Object span = spanClass.getMethod("current").invoke(null);
            Object spanContext = spanClass.getMethod("getSpanContext").invoke(span);
            Class<?> contextClass = loadClass("io.opentelemetry.api.trace.SpanContext");
            if (!Boolean.TRUE.equals(contextClass.getMethod("isValid").invoke(spanContext))) {
                return new String[2];
            }
            return new String[]{
                    asNonBlankString(contextClass.getMethod("getTraceId").invoke(spanContext)),
                    asNonBlankString(contextClass.getMethod("getSpanId").invoke(spanContext))
            };
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return new String[2];
        }
    }

    private static String[] readMdc() {
        try {
            Class<?> mdcClass = loadClass("org.slf4j.MDC");
            Method get = mdcClass.getMethod("get", String.class);
            return new String[]{
                    asNonBlankString(get.invoke(null, "trace.id")),
                    asNonBlankString(get.invoke(null, "span.id"))
            };
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return new String[2];
        }
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(className);
    }

    private static String asNonBlankString(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
