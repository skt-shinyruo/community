package com.nowcoder.community.im.gateway.shard;

import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

@Component
public class DiscoveredWorkerDescriptorFactory {

    private final ImGatewaySessionProperties properties;

    public DiscoveredWorkerDescriptorFactory(ImGatewaySessionProperties properties) {
        this.properties = properties;
    }

    public Optional<WorkerDescriptor> from(ServiceInstance instance) {
        if (instance == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = instance.getMetadata();
        ImGatewaySessionProperties.Worker workerProperties = properties.getWorker();
        if (!StringUtils.hasText(workerProperties.getWorkerIdMetadataKey())
                || !StringUtils.hasText(workerProperties.getWsPathMetadataKey())
                || !StringUtils.hasText(workerProperties.getWsPortMetadataKey())) {
            return Optional.empty();
        }
        String workerId = metadata.get(workerProperties.getWorkerIdMetadataKey());
        String wsPath = metadata.get(workerProperties.getWsPathMetadataKey());
        String wsPort = metadata.get(workerProperties.getWsPortMetadataKey());
        boolean draining = parseBoolean(metadata.get("draining"));
        int maxConnections = parseNonNegativeInt(metadata.get("maxConnections"));
        int activeConnectionHint = parseNonNegativeInt(metadata.get("activeConnectionHint"));
        String shardGroup = metadata.get("shardGroup");
        if (!StringUtils.hasText(workerId) || !StringUtils.hasText(wsPath) || !StringUtils.hasText(wsPort)
                || !StringUtils.hasText(instance.getHost())) {
            return Optional.empty();
        }

        String scheme = instance.isSecure() ? "wss" : "ws";
        String normalizedPath = wsPath.trim().startsWith("/") ? wsPath.trim() : "/" + wsPath.trim();
        Integer port = parsePort(wsPort);
        if (port == null) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(scheme, null, instance.getHost().trim(), port, normalizedPath, null, null);
            if (!scheme.equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != port) {
                return Optional.empty();
            }
            return Optional.of(new WorkerDescriptor(
                    workerId.trim(), uri, draining, maxConnections, activeConnectionHint, shardGroup
            ));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private static int parseNonNegativeInt(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Integer parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
