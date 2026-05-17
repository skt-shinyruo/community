package com.nowcoder.community.common.spring.feature;

import java.util.Map;

public class FeatureFlagDecisions {

    private final FeatureFlagProperties properties;

    public FeatureFlagDecisions(FeatureFlagProperties properties) {
        this.properties = properties;
    }

    public boolean enabled(String key) {
        if (key == null || key.isBlank() || properties == null) {
            return false;
        }

        Map<String, Boolean> flags = properties.getFlags();
        return flags != null && Boolean.TRUE.equals(flags.get(key));
    }
}
