package com.nowcoder.yierloom.plugins.method;

import java.lang.reflect.Method;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class MethodObservationHelper {
    private static final String PLUGIN_ID = "method";
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private MethodObservationHelper() {
    }

    public static void observe(
            String className,
            String methodName,
            String descriptor,
            long durationMs
    ) {
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            PluginObservation.Builder observation = PluginObservation.builder("method-call")
                    .attribute("method.class", className == null ? "-" : className)
                    .attribute("method.name", methodName == null ? "-" : methodName)
                    .attribute("method.descriptor", descriptor == null ? "-" : descriptor)
                    .longField("duration.ms", Math.max(0, durationMs));
            addTraceFields(observation, currentTraceFields());
            YierLoomBridge.observe(PLUGIN_ID, observation.build());
        } finally {
            OBSERVING.remove();
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
