package com.nowcoder.community.common.spring.degradation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "community")
public class DegradationProperties {

    private final Map<String, String> degradation = new LinkedHashMap<>();

    public Map<String, String> getModes() {
        return degradation;
    }

    public Map<String, String> getDegradation() {
        return degradation;
    }
}
