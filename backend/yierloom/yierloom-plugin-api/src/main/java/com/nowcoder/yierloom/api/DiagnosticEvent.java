package com.nowcoder.yierloom.api;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DiagnosticEvent(
        String action,
        Instant timestamp,
        Map<String, String> attributes,
        Map<String, Boolean> booleanFields,
        Map<String, Long> longFields,
        Map<String, Double> doubleFields
) {
    public DiagnosticEvent {
        action = Objects.requireNonNull(action, "action");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        booleanFields = Map.copyOf(Objects.requireNonNull(booleanFields, "booleanFields"));
        longFields = Map.copyOf(Objects.requireNonNull(longFields, "longFields"));
        doubleFields = Map.copyOf(Objects.requireNonNull(doubleFields, "doubleFields"));
    }

    public static Builder builder(String action) {
        return new Builder(action);
    }

    public static final class Builder {
        private final String action;
        private Instant timestamp;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final Map<String, Boolean> booleanFields = new LinkedHashMap<>();
        private final Map<String, Long> longFields = new LinkedHashMap<>();
        private final Map<String, Double> doubleFields = new LinkedHashMap<>();

        private Builder(String action) {
            this.action = Objects.requireNonNull(action, "action");
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder attribute(String key, String value) {
            key = Objects.requireNonNull(key, "key");
            value = Objects.requireNonNull(value, "value");
            removeTypedValues(key);
            attributes.put(key, value);
            return this;
        }

        public Builder booleanField(String key, boolean value) {
            key = Objects.requireNonNull(key, "key");
            removeTypedValues(key);
            booleanFields.put(key, value);
            return this;
        }

        public Builder longField(String key, long value) {
            key = Objects.requireNonNull(key, "key");
            removeTypedValues(key);
            longFields.put(key, value);
            return this;
        }

        public Builder doubleField(String key, double value) {
            key = Objects.requireNonNull(key, "key");
            removeTypedValues(key);
            doubleFields.put(key, value);
            return this;
        }

        public DiagnosticEvent build() {
            return new DiagnosticEvent(action, timestamp, attributes, booleanFields, longFields, doubleFields);
        }

        private void removeTypedValues(String key) {
            attributes.remove(key);
            booleanFields.remove(key);
            longFields.remove(key);
            doubleFields.remove(key);
        }
    }
}
