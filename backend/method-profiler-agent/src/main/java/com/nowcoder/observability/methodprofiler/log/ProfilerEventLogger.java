package com.nowcoder.observability.methodprofiler.log;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfilerEventLogger {

    private final PrintStream fallback;
    private final Object slf4jLogger;
    private final Method slf4jInfo;

    public ProfilerEventLogger() {
        this(System.err, Slf4jBinding.tryCreate());
    }

    public ProfilerEventLogger(PrintStream fallback) {
        this(fallback, null);
    }

    private ProfilerEventLogger(PrintStream fallback, Slf4jBinding binding) {
        this.fallback = fallback;
        this.slf4jLogger = binding == null ? null : binding.logger();
        this.slf4jInfo = binding == null ? null : binding.infoMethod();
    }

    public void logSummary(List<MethodSnapshot> snapshots, long droppedMethodKeys, Map<String, String> traceFields) {
        for (MethodSnapshot snapshot : snapshots) {
            Map<String, Object> fields = base("method_latency_summary", "success", traceFields);
            addMethod(fields, snapshot.key());
            fields.put("method.invocation.count", snapshot.count());
            fields.put("duration.avg.ms", snapshot.avgMs());
            fields.put("duration.max.ms", snapshot.maxMs());
            fields.put("duration.p95.ms", snapshot.p95Ms());
            if (droppedMethodKeys > 0) {
                fields.put("method.dropped.keys", droppedMethodKeys);
            }
            write(fields);
        }
    }

    public void logSlowCall(MethodKey key, long durationMs, long thresholdMs, Map<String, String> traceFields) {
        Map<String, Object> fields = base("method_slow_call", "threshold", traceFields);
        addMethod(fields, key);
        fields.put("duration.ms", durationMs);
        fields.put("threshold.ms", thresholdMs);
        write(fields);
    }

    private Map<String, Object> base(String action, String outcome, Map<String, String> traceFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("@timestamp", Instant.now().toString());
        fields.put("event.category", "method");
        fields.put("event.action", action);
        fields.put("event.outcome", outcome);
        if (traceFields != null) {
            traceFields.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    fields.put(key, value);
                }
            });
        }
        return fields;
    }

    private void addMethod(Map<String, Object> fields, MethodKey key) {
        fields.put("method.class", key.className());
        fields.put("method.name", key.methodName());
        fields.put("method.signature.hash", key.signatureHash());
    }

    private void write(Map<String, Object> fields) {
        try {
            String json = toJson(fields);
            if (slf4jLogger != null && slf4jInfo != null) {
                slf4jInfo.invoke(slf4jLogger, json);
            } else {
                fallback.println(json);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private String toJson(Map<String, Object> fields) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Slf4jBinding(Object logger, Method infoMethod) {

        static Slf4jBinding tryCreate() {
            try {
                Class<?> loggerFactoryClass = Class.forName("org.slf4j.LoggerFactory");
                Object logger = loggerFactoryClass.getMethod("getLogger", String.class)
                        .invoke(null, "method-profiler");
                Class<?> loggerClass = Class.forName("org.slf4j.Logger");
                Method info = loggerClass.getMethod("info", String.class);
                return new Slf4jBinding(logger, info);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
