package com.nowcoder.yierloom.plugins.exception;

import java.lang.reflect.Method;

import com.nowcoder.yierloom.api.PluginObservation;
import com.nowcoder.yierloom.api.YierLoomBridge;

public final class ExceptionObservationHelper {
    private static final String PLUGIN_ID = "exception";
    private static final ThreadLocal<Boolean> OBSERVING = new ThreadLocal<>();

    private ExceptionObservationHelper() {
    }

    public static void observe(
            String className,
            String methodName,
            String descriptor,
            Throwable throwable
    ) {
        if (throwable == null) {
            return;
        }
        if (Boolean.TRUE.equals(OBSERVING.get())) {
            return;
        }
        OBSERVING.set(Boolean.TRUE);
        try {
            PluginObservation.Builder observation = PluginObservation.builder("exception-thrown")
                    .attribute("method.class", className == null ? "-" : className)
                    .attribute("method.name", methodName == null ? "-" : methodName)
                    .attribute("method.descriptor", descriptor == null ? "-" : descriptor)
                    .attribute("exception.type", throwable.getClass().getName());

            String[] traceFields = currentTraceFields();
            if (traceFields[0] != null) {
                observation.attribute("trace.id", traceFields[0]);
            }
            if (traceFields[1] != null) {
                observation.attribute("span.id", traceFields[1]);
            }
            YierLoomBridge.observe(PLUGIN_ID, observation.build());
        } finally {
            OBSERVING.remove();
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
            Object valid = spanContextClass.getMethod("isValid").invoke(spanContext);
            if (!Boolean.TRUE.equals(valid)) {
                return emptyTraceFields();
            }
            String traceId = stringValue(
                    spanContextClass.getMethod("getTraceId").invoke(spanContext));
            String spanId = stringValue(
                    spanContextClass.getMethod("getSpanId").invoke(spanContext));
            return traceId == null
                    ? emptyTraceFields()
                    : new String[]{traceId, spanId};
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return emptyTraceFields();
        }
    }

    private static String[] readMdc() {
        try {
            Class<?> mdcClass = loadClass("org.slf4j.MDC");
            Method get = mdcClass.getMethod("get", String.class);
            String traceId = stringValue(get.invoke(null, "trace.id"));
            String spanId = stringValue(get.invoke(null, "span.id"));
            return new String[]{traceId, spanId};
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return emptyTraceFields();
        }
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (ClassNotFoundException ignored) {
                // Fall back to the Helper's defining loader.
            }
        }
        return Class.forName(className);
    }

    private static String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String[] emptyTraceFields() {
        return new String[2];
    }
}
