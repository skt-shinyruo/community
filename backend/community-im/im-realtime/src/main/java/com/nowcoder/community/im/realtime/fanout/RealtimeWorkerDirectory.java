package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
        LinkedHashMap<Integer, String> workerIdsByInboxSlot = new LinkedHashMap<>();
        List<ServiceInstance> instances = serviceInstancesSupplier.get();
        if (instances == null || instances.isEmpty()) {
            return Map.of();
        }
        for (ServiceInstance instance : instances) {
            RealtimeWorkerEndpoint endpoint = endpointFrom(instance);
            if (endpoints.containsKey(endpoint.workerId())) {
                throw new IllegalStateException("Duplicate realtime worker id: " + endpoint.workerId());
            }
            String existingWorkerId = workerIdsByInboxSlot.putIfAbsent(
                    endpoint.roomFanoutInboxSlot(),
                    endpoint.workerId()
            );
            if (existingWorkerId != null) {
                throw new IllegalStateException(
                        "Duplicate room fanout inbox slot: " + endpoint.roomFanoutInboxSlot()
                                + " for workers " + existingWorkerId + " and " + endpoint.workerId()
                );
            }
            endpoints.put(endpoint.workerId(), endpoint);
        }
        return Map.copyOf(endpoints);
    }

    private RealtimeWorkerEndpoint endpointFrom(ServiceInstance instance) {
        if (instance == null) {
            throw invalidInstance("instance is null");
        }
        Map<String, String> metadata = instance.getMetadata();
        String workerIdKey = StringUtils.hasText(sessionProperties.getWorkerIdMetadataKey())
                ? sessionProperties.getWorkerIdMetadataKey().trim()
                : "workerId";
        String workerId = metadata == null ? null : metadata.get(workerIdKey);
        if (!StringUtils.hasText(workerId)) {
            throw invalidInstance("missing worker id metadata '" + workerIdKey + "'");
        }
        String inboxSlotKey = fanoutProperties.normalizedWorkerInboxSlotMetadataKey();
        String rawInboxSlot = metadata == null ? null : metadata.get(inboxSlotKey);
        if (!StringUtils.hasText(rawInboxSlot)) {
            throw invalidInstance(
                    "missing room fanout inbox slot metadata '" + inboxSlotKey + "' for worker " + workerId.trim()
            );
        }
        int inboxSlot;
        try {
            inboxSlot = Integer.parseInt(rawInboxSlot.trim());
        } catch (NumberFormatException ex) {
            throw invalidInstance(
                    "malformed room fanout inbox slot '" + rawInboxSlot + "' for worker " + workerId.trim(),
                    ex
            );
        }
        int partitions = fanoutProperties.normalizedRoutedCommandPartitions();
        if (inboxSlot < 0 || inboxSlot >= partitions) {
            throw invalidInstance(
                    "room fanout inbox slot for worker " + workerId.trim()
                            + " must be between 0 and " + (partitions - 1)
            );
        }
        return new RealtimeWorkerEndpoint(workerId.trim(), inboxSlot);
    }

    private static IllegalStateException invalidInstance(String detail) {
        return new IllegalStateException("Invalid im-realtime-worker discovery instance: " + detail);
    }

    private static IllegalStateException invalidInstance(String detail, RuntimeException cause) {
        return new IllegalStateException("Invalid im-realtime-worker discovery instance: " + detail, cause);
    }
}
