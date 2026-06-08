package com.nowcoder.observability.runtimediagnostics.core;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiagnosticEventLogger {

    private final PrintStream output;
    private final String serviceName;

    public DiagnosticEventLogger() {
        this(System.out);
    }

    public DiagnosticEventLogger(PrintStream output) {
        this(output, resolveServiceName());
    }

    public DiagnosticEventLogger(PrintStream output, String serviceName) {
        this.output = output;
        this.serviceName = serviceName == null || serviceName.isBlank() ? "unknown" : serviceName;
    }

    public void log(DiagnosticEvent event) {
        if (event == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.putAll(event.fields());
        fields.put("service.name", serviceName);
        write(fields);
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
            if (value instanceof Double doubleValue && !Double.isFinite(doubleValue)) {
                builder.append('"').append(doubleValue).append('"');
            } else if (value instanceof Float floatValue && !Float.isFinite(floatValue)) {
                builder.append('"').append(floatValue).append('"');
            } else if (value instanceof Number || value instanceof Boolean) {
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
        String property = System.getProperty("runtime.diagnostics.service.name");
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
