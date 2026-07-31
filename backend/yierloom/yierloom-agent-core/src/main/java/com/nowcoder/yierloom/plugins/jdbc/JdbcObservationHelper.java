package com.nowcoder.yierloom.plugins.jdbc;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class JdbcObservationHelper {
    private static final String PLUGIN_ID = "jdbc";
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private JdbcObservationHelper() {
    }

    public static void observe(Object[] arguments, long durationMs, boolean error) {
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            YierLoomBridge.observe(PLUGIN_ID, describe(arguments, durationMs, error));
        } finally {
            OBSERVING.remove();
        }
    }

    public static PluginObservation describe(
            Object[] arguments,
            long durationMs,
            boolean error
    ) {
        String sql = firstString(arguments);
        String operation = "unknown";
        String statementHash = "unknown";
        if (sql != null && !sql.isBlank()) {
            String normalized = sql.replaceAll("'[^']*'", "?")
                    .replaceAll("\\b\\d+\\b", "?")
                    .replaceAll("\\s+", " ")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            String candidate = normalized.split(" ", 2)[0];
            if (candidate.matches("select|insert|update|delete|merge|call")) {
                operation = candidate;
            }
            statementHash = hash16(normalized);
        }

        PluginObservation.Builder observation = PluginObservation.builder("dependency-call")
                .attribute("db.system", "jdbc")
                .attribute("db.operation", operation)
                .attribute("db.statement.hash", statementHash)
                .booleanField("error", error)
                .longField("duration.ms", Math.max(0, durationMs));
        addTraceFields(observation, currentTraceFields());
        return observation.build();
    }

    private static String firstString(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof String value) {
                return value;
            }
        }
        return null;
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

    private static void addTraceFields(PluginObservation.Builder observation, String[] traceFields) {
        if (traceFields[0] != null && !traceFields[0].isBlank()) {
            observation.attribute("trace.id", traceFields[0]);
        }
        if (traceFields[1] != null && !traceFields[1].isBlank()) {
            observation.attribute("span.id", traceFields[1]);
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
            Class<?> spanContextClass = loadClass("io.opentelemetry.api.trace.SpanContext");
            if (!Boolean.TRUE.equals(spanContextClass.getMethod("isValid").invoke(spanContext))) {
                return new String[2];
            }
            return new String[]{
                    asNonBlankString(spanContextClass.getMethod("getTraceId").invoke(spanContext)),
                    asNonBlankString(spanContextClass.getMethod("getSpanId").invoke(spanContext))
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
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }
}
