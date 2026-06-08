package com.nowcoder.observability.methodprofiler.trace;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class TraceContextReader {

    public Map<String, String> currentTraceFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        readOtel(fields);
        if (!fields.containsKey("trace.id")) {
            readMdc(fields);
        }
        return fields;
    }

    private void readOtel(Map<String, String> fields) {
        try {
            Class<?> spanClass = Class.forName("io.opentelemetry.api.trace.Span");
            Object span = spanClass.getMethod("current").invoke(null);
            Object spanContext = spanClass.getMethod("getSpanContext").invoke(span);
            Class<?> spanContextClass = Class.forName("io.opentelemetry.api.trace.SpanContext");
            boolean valid = (Boolean) spanContextClass.getMethod("isValid").invoke(spanContext);
            if (!valid) {
                return;
            }
            fields.put("trace.id", String.valueOf(spanContextClass.getMethod("getTraceId").invoke(spanContext)));
            fields.put("span.id", String.valueOf(spanContextClass.getMethod("getSpanId").invoke(spanContext)));
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    private void readMdc(Map<String, String> fields) {
        try {
            Class<?> mdcClass = Class.forName("org.slf4j.MDC");
            Method get = mdcClass.getMethod("get", String.class);
            Object traceId = get.invoke(null, "trace.id");
            Object spanId = get.invoke(null, "span.id");
            if (traceId instanceof String value && !value.isBlank()) {
                fields.put("trace.id", value);
            }
            if (spanId instanceof String value && !value.isBlank()) {
                fields.put("span.id", value);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }
}
