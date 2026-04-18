package com.nowcoder.community.gateway.shard;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class WorkerRegistry {

    private final Supplier<List<WorkerDescriptor>> workerSupplier;
    private final Set<String> unhealthyWorkerIds = new LinkedHashSet<>();

    public WorkerRegistry(
            DiscoveryClient discoveryClient,
            WorkerDiscoveryProperties properties,
            DiscoveredWorkerDescriptorFactory factory
    ) {
        this(() -> discoveryClient.getInstances(properties.getServiceId()).stream()
                .map(factory::from)
                .flatMap(Optional::stream)
                .toList());
    }

    public WorkerRegistry(List<WorkerDescriptor> workers) {
        List<WorkerDescriptor> snapshot = workers == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(workers));
        assertNoDuplicateWorkerIds(snapshot);
        this.workerSupplier = () -> snapshot;
    }

    private WorkerRegistry(Supplier<List<WorkerDescriptor>> workerSupplier) {
        this.workerSupplier = workerSupplier;
    }

    public synchronized List<WorkerDescriptor> allWorkers() {
        return currentWorkers();
    }

    public synchronized List<WorkerDescriptor> healthyWorkers() {
        List<WorkerDescriptor> workers = currentWorkers();
        ArrayList<WorkerDescriptor> healthy = new ArrayList<>();
        for (WorkerDescriptor worker : workers) {
            if (!unhealthyWorkerIds.contains(worker.getId())) {
                healthy.add(worker);
            }
        }
        return List.copyOf(healthy);
    }

    public synchronized Optional<WorkerDescriptor> find(String workerId) {
        if (!StringUtils.hasText(workerId)) {
            return Optional.empty();
        }
        return currentWorkers().stream()
                .filter(worker -> workerId.equals(worker.getId()))
                .findFirst();
    }

    public synchronized void markUnhealthy(String workerId) {
        if (StringUtils.hasText(workerId)) {
            unhealthyWorkerIds.add(workerId);
        }
    }

    public synchronized void markHealthy(String workerId) {
        unhealthyWorkerIds.remove(workerId);
    }

    private static void assertNoDuplicateWorkerIds(List<WorkerDescriptor> workers) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (WorkerDescriptor worker : workers) {
            if (worker == null || !StringUtils.hasText(worker.getId()) || worker.getUri() == null) {
                continue;
            }
            if (!ids.add(worker.getId())) {
                throw new IllegalArgumentException("Duplicate worker id: " + worker.getId());
            }
        }
    }

    private List<WorkerDescriptor> currentWorkers() {
        LinkedHashMap<String, WorkerDescriptor> byId = new LinkedHashMap<>();
        for (WorkerDescriptor worker : workerSupplier.get()) {
            if (worker == null || !StringUtils.hasText(worker.getId()) || worker.getUri() == null) {
                continue;
            }
            WorkerDescriptor previous = byId.putIfAbsent(worker.getId(), worker);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate worker id: " + worker.getId());
            }
        }
        unhealthyWorkerIds.retainAll(byId.keySet());
        return List.copyOf(byId.values());
    }
}
