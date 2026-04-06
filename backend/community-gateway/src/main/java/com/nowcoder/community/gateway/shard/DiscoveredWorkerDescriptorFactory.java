package com.nowcoder.community.gateway.shard;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class DiscoveredWorkerDescriptorFactory {

    private final WorkerDiscoveryProperties properties;

    public DiscoveredWorkerDescriptorFactory(WorkerDiscoveryProperties properties) {
        this.properties = properties;
    }

    public Optional<WorkerDescriptor> from(ServiceInstance instance) {
        if (instance == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = instance.getMetadata();
        String workerId = metadata.get(properties.getWorkerIdMetadataKey());
        String wsPath = metadata.get(properties.getWsPathMetadataKey());
        String wsPort = metadata.get(properties.getWsPortMetadataKey());
        if (!StringUtils.hasText(workerId) || !StringUtils.hasText(wsPath) || !StringUtils.hasText(wsPort)) {
            return Optional.empty();
        }

        String scheme = instance.isSecure() ? "wss" : "ws";
        String normalizedPath = wsPath.trim().startsWith("/") ? wsPath.trim() : "/" + wsPath.trim();
        URI uri = URI.create(scheme + "://" + instance.getHost() + ":" + wsPort.trim() + normalizedPath);
        return Optional.of(new WorkerDescriptor(workerId.trim(), uri));
    }
}
