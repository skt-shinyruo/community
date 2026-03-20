package com.nowcoder.community.gateway.shard;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerRegistryTest {

    @Test
    void shouldExposeConfiguredWorkersAsHealthyByDefault() {
        WorkerRegistry registry = new WorkerRegistry(properties(
                worker("worker-a", "ws://127.0.0.1:18081/ws/im"),
                worker("worker-b", "ws://127.0.0.1:18082/ws/im")
        ));

        assertThat(registry.allWorkers())
                .extracting(WorkerDescriptor::getId)
                .containsExactly("worker-a", "worker-b");
        assertThat(registry.healthyWorkers())
                .extracting(WorkerDescriptor::getId)
                .containsExactly("worker-a", "worker-b");
        assertThat(registry.find("worker-a"))
                .map(WorkerDescriptor::getUri)
                .contains(URI.create("ws://127.0.0.1:18081/ws/im"));
    }

    @Test
    void shouldExcludeWorkersMarkedUnhealthyUntilRestored() {
        WorkerRegistry registry = new WorkerRegistry(properties(
                worker("worker-a", "ws://127.0.0.1:18081/ws/im"),
                worker("worker-b", "ws://127.0.0.1:18082/ws/im"),
                worker("worker-c", "ws://127.0.0.1:18083/ws/im")
        ));

        registry.markUnhealthy("worker-b");

        assertThat(registry.healthyWorkers())
                .extracting(WorkerDescriptor::getId)
                .containsExactly("worker-a", "worker-c");

        registry.markHealthy("worker-b");

        assertThat(registry.healthyWorkers())
                .extracting(WorkerDescriptor::getId)
                .containsExactly("worker-a", "worker-b", "worker-c");
    }

    @Test
    void shouldRejectDuplicateWorkerIds() {
        WorkerRegistryProperties properties = properties(
                worker("worker-a", "ws://127.0.0.1:18081/ws/im"),
                worker("worker-a", "ws://127.0.0.1:18082/ws/im")
        );

        assertThatThrownBy(() -> new WorkerRegistry(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate worker id");
    }

    private static WorkerRegistryProperties properties(WorkerRegistryProperties.Worker... workers) {
        WorkerRegistryProperties properties = new WorkerRegistryProperties();
        for (WorkerRegistryProperties.Worker worker : workers) {
            properties.getWorkers().add(worker);
        }
        return properties;
    }

    private static WorkerRegistryProperties.Worker worker(String id, String uri) {
        WorkerRegistryProperties.Worker worker = new WorkerRegistryProperties.Worker();
        worker.setId(id);
        worker.setUri(URI.create(uri));
        return worker;
    }
}
