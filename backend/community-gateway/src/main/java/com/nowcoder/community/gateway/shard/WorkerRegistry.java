package com.nowcoder.community.gateway.shard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class WorkerRegistry {

    private final LinkedHashMap<String, WorkerDescriptor> byId = new LinkedHashMap<>();
    private final Set<String> unhealthyWorkerIds = new LinkedHashSet<>();

    public WorkerRegistry(WorkerRegistryProperties properties) {
        for (WorkerRegistryProperties.Worker worker : properties.getWorkers()) {
            if (worker == null || worker.getId() == null || worker.getId().isBlank() || worker.getUri() == null) {
                continue;
            }
            WorkerDescriptor previous = byId.putIfAbsent(worker.getId(), new WorkerDescriptor(worker.getId(), worker.getUri()));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate worker id: " + worker.getId());
            }
        }
    }

    public List<WorkerDescriptor> allWorkers() {
        return List.copyOf(byId.values());
    }

    public List<WorkerDescriptor> healthyWorkers() {
        ArrayList<WorkerDescriptor> list = new ArrayList<>();
        for (Map.Entry<String, WorkerDescriptor> entry : byId.entrySet()) {
            if (!unhealthyWorkerIds.contains(entry.getKey())) {
                list.add(entry.getValue());
            }
        }
        return List.copyOf(list);
    }

    public Optional<WorkerDescriptor> find(String workerId) {
        return Optional.ofNullable(byId.get(workerId));
    }

    public void markUnhealthy(String workerId) {
        if (workerId != null && byId.containsKey(workerId)) {
            unhealthyWorkerIds.add(workerId);
        }
    }

    public void markHealthy(String workerId) {
        unhealthyWorkerIds.remove(workerId);
    }
}
