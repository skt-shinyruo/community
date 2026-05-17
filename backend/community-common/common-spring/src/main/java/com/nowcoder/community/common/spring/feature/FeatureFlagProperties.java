package com.nowcoder.community.common.spring.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "community")
public class FeatureFlagProperties {

    private final Map<String, Boolean> features = new LinkedHashMap<>();

    public Map<String, Boolean> getFlags() {
        return features;
    }

    public Map<String, Boolean> getFeatures() {
        return features;
    }
}
