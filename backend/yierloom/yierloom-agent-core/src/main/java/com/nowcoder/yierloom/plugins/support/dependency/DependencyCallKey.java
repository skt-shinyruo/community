package com.nowcoder.yierloom.plugins.support.dependency;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record DependencyCallKey(String pluginId, Map<String, String> dimensions) {
    public DependencyCallKey {
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId must not be blank");
        }
        Objects.requireNonNull(dimensions, "dimensions");
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }
}
