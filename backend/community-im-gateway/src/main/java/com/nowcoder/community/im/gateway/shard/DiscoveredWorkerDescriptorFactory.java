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

    private static final String INTERNAL_WS_PATH = "/internal/ws/im";

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
        if (!StringUtils.hasText(workerProperties.getWorkerIdMetadataKey())) {
            return Optional.empty();
        }
        String workerId = metadata.get(workerProperties.getWorkerIdMetadataKey());
        boolean draining = parseBoolean(metadata.get("draining"));
        int maxConnections = parseNonNegativeInt(metadata.get("maxConnections"));
        int activeConnectionHint = parseNonNegativeInt(metadata.get("activeConnectionHint"));
        if (!StringUtils.hasText(workerId) || !StringUtils.hasText(instance.getHost())
                || instance.getPort() < 1 || instance.getPort() > 65535) {
            return Optional.empty();
        }

        String scheme = instance.isSecure() ? "wss" : "ws";
        try {
            URI uri = new URI(scheme, null, instance.getHost().trim(), instance.getPort(), INTERNAL_WS_PATH, null, null);
            if (!scheme.equals(uri.getScheme()) || uri.getHost() == null || uri.getPort() != instance.getPort()) {
                return Optional.empty();
            }
            return Optional.of(new WorkerDescriptor(
                    workerId.trim(), uri, draining, maxConnections, activeConnectionHint
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

}
