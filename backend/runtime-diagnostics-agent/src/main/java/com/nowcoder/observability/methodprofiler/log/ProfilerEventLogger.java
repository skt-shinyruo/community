package com.nowcoder.observability.methodprofiler.log;

import com.nowcoder.observability.methodprofiler.model.MethodKey;
import com.nowcoder.observability.methodprofiler.model.MethodSnapshot;

import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfilerEventLogger {

    private final PrintStream output;
    private final String serviceName;

    public ProfilerEventLogger() {
        this(System.out);
    }

    public ProfilerEventLogger(PrintStream output) {
        this(output, resolveServiceName());
    }

    ProfilerEventLogger(PrintStream output, String serviceName) {
        this.output = output;
        this.serviceName = serviceName == null || serviceName.isBlank() ? "unknown" : serviceName;
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
        fields.put("service.name", serviceName);
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
            output.println(toJson(fields));
        } catch (RuntimeException ignored) {
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
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String resolveServiceName() {
        String property = System.getProperty("method.profiler.service.name");
        if (property != null && !property.isBlank()) {
            return property;
        }
        property = System.getProperty("otel.service.name");
        if (property != null && !property.isBlank()) {
            return property;
        }
        String environment = System.getenv("OTEL_SERVICE_NAME");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        environment = System.getenv("SERVICE_NAME");
        if (environment != null && !environment.isBlank()) {
            return environment;
        }
        return "unknown";
    }
}
