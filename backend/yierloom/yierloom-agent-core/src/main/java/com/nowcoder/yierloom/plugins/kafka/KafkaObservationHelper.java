package com.nowcoder.yierloom.plugins.kafka;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class KafkaObservationHelper {
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private KafkaObservationHelper() {
    }

    public static void observe(
            Object[] arguments,
            long durationMs,
            boolean error,
            boolean topicNamesEnabled
    ) {
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            YierLoomBridge.observe(
                    "kafka",
                    describe(arguments, durationMs, error, topicNamesEnabled));
        } finally {
            OBSERVING.remove();
        }
    }

    public static PluginObservation describe(
            Object[] arguments,
            long durationMs,
            boolean error,
            boolean topicNamesEnabled
    ) {
        String topic = firstTopic(arguments);
        String destination = topic == null || topic.isBlank()
                ? "unknown"
                : topicNamesEnabled ? safeTopic(topic) : hash16(topic);
        PluginObservation.Builder observation = PluginObservation.builder("dependency-call")
                .attribute("messaging.operation", "produce")
                .attribute("messaging.destination.name", destination)
                .longField("duration.ms", Math.max(0, durationMs))
                .booleanField("error", error);
        addTraceFields(observation, currentTraceFields());
        return observation.build();
    }

    private static String firstTopic(Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return null;
        }
        return arguments[0] instanceof String topic ? topic : null;
    }

    private static String safeTopic(String topic) {
        int length = Math.min(topic.length(), 512);
        if (length > 0
                && length < topic.length()
                && Character.isHighSurrogate(topic.charAt(length - 1))
                && Character.isLowSurrogate(topic.charAt(length))) {
            length--;
        }
        StringBuilder safe = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            char character = topic.charAt(index);
            safe.append(Character.isISOControl(character) ? '_' : character);
        }
        return safe.toString();
    }

    private static String hash16(String value) {
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
