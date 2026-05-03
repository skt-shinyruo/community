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
            return Optional.of(new WorkerDescriptor(workerId.trim(), uri));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private static Integer parsePort(String value) {
        String trimmed = value.trim();
        int port = 0;
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (ch < '0' || ch > '9') {
                return null;
            }
            port = port * 10 + ch - '0';
            if (port > 65535) {
                return null;
            }
        }
        return port >= 1 ? port : null;
    }
}
