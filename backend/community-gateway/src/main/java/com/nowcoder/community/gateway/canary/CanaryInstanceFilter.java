package com.nowcoder.community.gateway.canary;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CanaryInstanceFilter {

    public List<ServiceInstance> filter(List<ServiceInstance> instances, CanaryRouteProperties.Selector selector) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        Map<String, String> required = selector == null ? Map.of() : selector.getMetadata();
        return instances.stream()
                .filter(instance -> instance != null)
                .filter(instance -> !isDraining(instance))
                .filter(instance -> matches(instance, required))
                .toList();
    }

    private static boolean isDraining(ServiceInstance instance) {
        String draining = metadata(instance).getOrDefault("draining", "false");
        if (draining == null) {
            return false;
        }
        return "true".equalsIgnoreCase(draining.trim());
    }

    private static boolean matches(ServiceInstance instance, Map<String, String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Map<String, String> metadata = metadata(instance);
        for (Map.Entry<String, String> entry : required.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                return false;
            }
            if (!entry.getValue().equals(metadata.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> metadata(ServiceInstance instance) {
        Map<String, String> metadata = instance.getMetadata();
        return metadata == null ? Map.of() : metadata;
    }
}
