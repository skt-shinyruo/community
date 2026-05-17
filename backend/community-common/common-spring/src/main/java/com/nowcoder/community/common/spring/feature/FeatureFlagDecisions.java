package com.nowcoder.community.common.spring.feature;

import java.util.Map;

public class FeatureFlagDecisions {

    private final FeatureFlagProperties properties;

    public FeatureFlagDecisions(FeatureFlagProperties properties) {
        this.properties = properties;
    }

    public boolean enabled(String key) {
        return enabledOrDefault(key, false);
    }

    public boolean enabledOrDefault(String key, boolean defaultValue) {
        if (key == null || key.isBlank() || properties == null) {
            return defaultValue;
        }

        Map<String, Boolean> flags = properties.getFlags();
        if (flags == null || !flags.containsKey(key)) {
            return defaultValue;
        }
        return Boolean.TRUE.equals(flags.get(key));
    }
}
