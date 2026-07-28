package com.nowcoder.yierloom.api;

import java.util.Objects;

public final class PluginConfigurationException extends IllegalArgumentException {
    private final String key;
    private final String expectedType;

    public PluginConfigurationException(String key, String expectedType) {
        super("invalid plugin configuration key '" + key + "', expected " + expectedType);
        this.key = Objects.requireNonNull(key);
        this.expectedType = Objects.requireNonNull(expectedType);
    }

    public String key() {
        return key;
    }

    public String expectedType() {
        return expectedType;
    }
}
