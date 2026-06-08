package com.nowcoder.observability.runtimediagnostics.core;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record DiagnosticEvent(Map<String, Object> fields) {

    public DiagnosticEvent {
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static Builder builder(String action, String outcome, String probe) {
        return new Builder(action, outcome, probe);
    }

    public static final class Builder {
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String action, String outcome, String probe) {
            fields.put("@timestamp", Instant.now().toString());
            fields.put("event.category", "runtime_diagnostics");
            fields.put("event.action", action);
            fields.put("event.outcome", outcome);
            fields.put("diagnostic.agent.name", "runtime-diagnostics-agent");
            fields.put("diagnostic.probe", probe);
        }

        public Builder put(String key, Object value) {
            if (key != null && !key.isBlank() && value != null) {
                fields.put(key, value);
            }
            return this;
        }

        public Builder putTraceFields(Map<String, String> traceFields) {
            if (traceFields != null) {
                traceFields.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                        fields.put(key, value);
                    }
                });
            }
            return this;
        }

        public DiagnosticEvent build() {
            return new DiagnosticEvent(fields);
        }
    }
}
