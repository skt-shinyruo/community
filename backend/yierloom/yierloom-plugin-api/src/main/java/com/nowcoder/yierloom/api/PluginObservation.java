package com.nowcoder.yierloom.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record PluginObservation(
        String type,
        Map<String, String> attributes,
        Map<String, Boolean> booleanFields,
        Map<String, Long> longFields,
        Map<String, Double> doubleFields
) {
    public PluginObservation {
        type = Objects.requireNonNull(type, "type");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        booleanFields = Map.copyOf(Objects.requireNonNull(booleanFields, "booleanFields"));
        longFields = Map.copyOf(Objects.requireNonNull(longFields, "longFields"));
        doubleFields = Map.copyOf(Objects.requireNonNull(doubleFields, "doubleFields"));
    }

    public static Builder builder(String type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final String type;
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final Map<String, Boolean> booleanFields = new LinkedHashMap<>();
        private final Map<String, Long> longFields = new LinkedHashMap<>();
        private final Map<String, Double> doubleFields = new LinkedHashMap<>();

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type");
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

        public PluginObservation build() {
            return new PluginObservation(type, attributes, booleanFields, longFields, doubleFields);
        }

        private void removeTypedValues(String key) {
            attributes.remove(key);
            booleanFields.remove(key);
            longFields.remove(key);
            doubleFields.remove(key);
        }
    }
}
