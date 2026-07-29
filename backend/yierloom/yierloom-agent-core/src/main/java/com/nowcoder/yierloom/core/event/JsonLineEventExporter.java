package com.nowcoder.yierloom.core.event;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.nowcoder.yierloom.api.DiagnosticEvent;

@FunctionalInterface
interface EventExporter {
    void export(String pluginId, DiagnosticEvent event);
}

final class JsonLineEventExporter implements EventExporter {
    private static final String AGENT_NAME = "YierLoom";
    private static final String EVENT_CATEGORY = "yierloom";

    private final String serviceName;
    private final Clock clock;
    private final PrintStream output;

    JsonLineEventExporter(String serviceName, Clock clock, PrintStream output) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public void export(String pluginId, DiagnosticEvent event) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(event, "event");

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.putAll(event.attributes());
        fields.putAll(event.booleanFields());
        fields.putAll(event.longFields());
        fields.putAll(event.doubleFields());

        Instant timestamp = event.timestamp() == null ? clock.instant() : event.timestamp();
        fields.put("@timestamp", timestamp.toString());
        fields.put("event.category", EVENT_CATEGORY);
        fields.put("event.action", event.action());
        fields.put("diagnostic.agent.name", AGENT_NAME);
        fields.put("diagnostic.plugin.id", pluginId);
        fields.put("service.name", serviceName);

        output.println(toJson(fields));
        if (output.checkError()) {
            throw new IllegalStateException("JSON-lines exporter write failed");
        }
    }

    private static String toJson(Map<String, Object> fields) {
        StringBuilder json = new StringBuilder(128).append('{');
        boolean first = true;
        for (Map.Entry<String, Object> field : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendQuoted(json, field.getKey());
            json.append(':');
            appendValue(json, field.getValue());
        }
        return json.append('}').toString();
    }

    private static void appendValue(StringBuilder json, Object value) {
        if (value instanceof Boolean || value instanceof Long) {
            json.append(value);
            return;
        }
        if (value instanceof Double doubleValue && Double.isFinite(doubleValue)) {
            json.append(doubleValue);
            return;
        }
        appendQuoted(json, String.valueOf(value));
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
