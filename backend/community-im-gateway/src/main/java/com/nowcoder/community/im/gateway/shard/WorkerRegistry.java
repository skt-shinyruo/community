package com.nowcoder.community.im.gateway.shard;

import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Component
public class WorkerRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerRegistry.class);

    private final Supplier<List<WorkerDescriptor>> workerSupplier;
    private final Set<String> unhealthyWorkerIds = new LinkedHashSet<>();

    @Autowired
    public WorkerRegistry(
            DiscoveryClient discoveryClient,
            ImGatewaySessionProperties properties,
            DiscoveredWorkerDescriptorFactory factory
    ) {
        this(() -> discoveryClient.getInstances(properties.getWorker().getServiceId()).stream()
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

    WorkerRegistry(Supplier<List<WorkerDescriptor>> workerSupplier) {
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
            if (!isValid(worker)) {
                continue;
            }
            if (!ids.add(worker.getId())) {
                throw new IllegalArgumentException("Duplicate worker id: " + worker.getId());
            }
        }
    }

    private List<WorkerDescriptor> currentWorkers() {
        LinkedHashMap<String, WorkerDescriptor> byId = new LinkedHashMap<>();
        for (WorkerDescriptor worker : suppliedWorkers()) {
            if (!isValid(worker)) {
                continue;
            }
            WorkerDescriptor previous = byId.putIfAbsent(worker.getId(), worker);
            if (previous != null) {
                log.warn("Duplicate IM websocket worker id discovered; failing worker selection closed: workerId={}",
                        worker.getId());
                throw new DuplicateWorkerIdException(worker.getId());
            }
        }
        unhealthyWorkerIds.retainAll(byId.keySet());
        return List.copyOf(byId.values());
    }

    private List<WorkerDescriptor> suppliedWorkers() {
        try {
            List<WorkerDescriptor> workers = workerSupplier.get();
            return workers == null ? List.of() : workers;
        } catch (DuplicateWorkerIdException | WorkerDiscoveryException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("IM websocket worker discovery failed; failing worker selection closed: errorType={}",
                    ex.getClass().getName());
            throw new WorkerDiscoveryException(ex);
        }
    }

    private static boolean isValid(WorkerDescriptor worker) {
        return worker != null && !worker.isDraining() && StringUtils.hasText(worker.getId()) && worker.getUri() != null;
    }
}

class DuplicateWorkerIdException extends RuntimeException {

    private final String workerId;

    DuplicateWorkerIdException(String workerId) {
        super("Duplicate worker id: " + workerId);
        this.workerId = workerId;
    }

    String getWorkerId() {
        return workerId;
    }
}

class WorkerDiscoveryException extends RuntimeException {

    WorkerDiscoveryException(Throwable cause) {
        super("Worker discovery failed", cause);
    }
}
