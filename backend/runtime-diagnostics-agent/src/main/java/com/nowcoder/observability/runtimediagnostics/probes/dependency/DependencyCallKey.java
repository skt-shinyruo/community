package com.nowcoder.observability.runtimediagnostics.probes.dependency;

import java.util.LinkedHashMap;
import java.util.Map;

public record DependencyCallKey(String probe, Map<String, String> dimensions) {

    public DependencyCallKey {
        if (probe == null || probe.isBlank()) {
            throw new IllegalArgumentException("probe must not be blank");
        }
        dimensions = dimensions == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(dimensions));
    }
}
