package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class RealtimeWorkerDirectory {

    private final Supplier<List<ServiceInstance>> serviceInstancesSupplier;
    private final ImSessionProperties sessionProperties;
    private final RoomFanoutProperties fanoutProperties;
    private volatile long cacheExpiresAtNanos;
    private volatile Map<String, RealtimeWorkerEndpoint> cachedEndpoints = Map.of();

    @Autowired
    public RealtimeWorkerDirectory(
            DiscoveryClient discoveryClient,
            ImSessionProperties sessionProperties,
            RoomFanoutProperties fanoutProperties
    ) {
        this(() -> discoveryClient.getInstances(sessionProperties.getWorkerServiceId()),
                sessionProperties,
                fanoutProperties);
    }

    RealtimeWorkerDirectory(
            Supplier<List<ServiceInstance>> serviceInstancesSupplier,
            ImSessionProperties sessionProperties,
            RoomFanoutProperties fanoutProperties
    ) {
        this.serviceInstancesSupplier = serviceInstancesSupplier;
        this.sessionProperties = sessionProperties == null ? new ImSessionProperties() : sessionProperties;
        this.fanoutProperties = fanoutProperties == null ? new RoomFanoutProperties() : fanoutProperties;
    }

    public Optional<RealtimeWorkerEndpoint> find(String workerId) {
        if (!StringUtils.hasText(workerId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot().get(workerId.trim()));
    }

    private synchronized Map<String, RealtimeWorkerEndpoint> snapshot() {
        Duration cacheTtl = fanoutProperties.normalizedWorkerDirectoryCacheTtl();
        long now = System.nanoTime();
        if (!cachedEndpoints.isEmpty() && !cacheTtl.isZero() && now < cacheExpiresAtNanos) {
            return cachedEndpoints;
        }
        Map<String, RealtimeWorkerEndpoint> endpoints = resolveEndpoints();
        cachedEndpoints = endpoints;
        cacheExpiresAtNanos = cacheTtl.isZero() ? now : now + cacheTtl.toNanos();
        return endpoints;
    }

    private Map<String, RealtimeWorkerEndpoint> resolveEndpoints() {
        LinkedHashMap<String, RealtimeWorkerEndpoint> endpoints = new LinkedHashMap<>();
        List<ServiceInstance> instances = serviceInstancesSupplier.get();
        if (instances == null || instances.isEmpty()) {
            return Map.of();
        }
        for (ServiceInstance instance : instances) {
            Optional<RealtimeWorkerEndpoint> endpoint = endpointFrom(instance);
            if (endpoint.isEmpty()) {
                continue;
            }
            RealtimeWorkerEndpoint previous = endpoints.putIfAbsent(endpoint.get().workerId(), endpoint.get());
            if (previous != null) {
                throw new IllegalStateException("Duplicate realtime worker id: " + endpoint.get().workerId());
            }
            failOnDuplicateInboxSlot(endpoints, endpoint.get());
        }
        return Map.copyOf(endpoints);
    }

    private void failOnDuplicateInboxSlot(
            Map<String, RealtimeWorkerEndpoint> endpoints,
            RealtimeWorkerEndpoint endpoint
    ) {
        if (endpoint.roomFanoutInboxSlot() == null) {
            return;
        }
        for (RealtimeWorkerEndpoint existing : endpoints.values()) {
            if (endpoint.workerId().equals(existing.workerId()) || existing.roomFanoutInboxSlot() == null) {
                continue;
            }
            if (endpoint.roomFanoutInboxSlot().equals(existing.roomFanoutInboxSlot())) {
                throw new IllegalStateException(
                        "Duplicate room fanout inbox slot: " + endpoint.roomFanoutInboxSlot()
                                + " for workers " + existing.workerId() + " and " + endpoint.workerId()
                );
            }
        }
    }

    private Optional<RealtimeWorkerEndpoint> endpointFrom(ServiceInstance instance) {
        if (instance == null || !StringUtils.hasText(instance.getHost()) || instance.getPort() <= 0) {
            return Optional.empty();
        }
        Map<String, String> metadata = instance.getMetadata();
        String workerIdKey = StringUtils.hasText(sessionProperties.getWorkerIdMetadataKey())
                ? sessionProperties.getWorkerIdMetadataKey().trim()
                : "workerId";
        String workerId = metadata == null ? null : metadata.get(workerIdKey);
        if (!StringUtils.hasText(workerId)) {
            return Optional.empty();
        }
        String protocol = metadata == null ? null : metadata.get("protocol");
        String scheme = StringUtils.hasText(protocol) ? protocol.trim() : (instance.isSecure() ? "https" : "http");
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(
                    scheme,
                    null,
                    instance.getHost().trim(),
                    instance.getPort(),
                    fanoutProperties.normalizedTargetPath(),
                    null,
                    null
            );
            Integer inboxSlot = inboxSlot(metadata);
            return Optional.of(new RealtimeWorkerEndpoint(workerId.trim(), uri, inboxSlot));
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private Integer inboxSlot(Map<String, String> metadata) {
        if (metadata == null) {
            return null;
        }
        String raw = metadata.get(fanoutProperties.normalizedWorkerInboxSlotMetadataKey());
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0 || value >= fanoutProperties.normalizedRoutedCommandPartitions()) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
