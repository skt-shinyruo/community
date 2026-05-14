package com.nowcoder.community.common.observability.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RuntimeLogEvent {

    private final String category;
    private final String action;
    private final String outcome;
    private final String message;
    private final Map<String, String> fields;

    private RuntimeLogEvent(Builder builder) {
        this.category = requireText(builder.category, "category");
        this.action = requireText(builder.action, "action");
        this.outcome = requireText(builder.outcome, "outcome");
        this.message = requireText(builder.message, "message");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
    }

    public static Builder builder(String category, String action, String outcome, String message) {
        return new Builder(category, action, outcome, message);
    }

    public String category() {
        return category;
    }

    public String action() {
        return action;
    }

    public String outcome() {
        return outcome;
    }

    public String message() {
        return message;
    }

    public Map<String, String> fields() {
        return fields;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static final class Builder {

        private final String category;
        private final String action;
        private final String outcome;
        private final String message;
        private final Map<String, String> fields = new LinkedHashMap<>();

        private Builder(String category, String action, String outcome, String message) {
            this.category = category;
            this.action = action;
            this.outcome = outcome;
            this.message = message;
        }

        public Builder field(String key, Object value) {
            if (key == null || key.isBlank() || value == null) {
                return this;
            }
            fields.put(key, Objects.toString(value));
            return this;
        }

        public Builder fields(Map<String, ?> values) {
            if (values == null) {
                return this;
            }
            values.forEach(this::field);
            return this;
        }

        public RuntimeLogEvent build() {
            return new RuntimeLogEvent(this);
        }
    }
}
