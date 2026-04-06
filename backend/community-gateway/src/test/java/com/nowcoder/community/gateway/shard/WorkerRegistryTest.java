package com.nowcoder.community.gateway.shard;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerRegistryTest {

    @Test
    void shouldExposeConfiguredWorkersAsHealthyByDefault() {
        WorkerRegistry registry = new WorkerRegistry(List.of(
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
        WorkerRegistry registry = new WorkerRegistry(List.of(
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
        assertThatThrownBy(() -> new WorkerRegistry(List.of(
                worker("worker-a", "ws://127.0.0.1:18081/ws/im"),
                worker("worker-a", "ws://127.0.0.1:18082/ws/im")
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate worker id");
    }

    private static WorkerDescriptor worker(String id, String uri) {
        return new WorkerDescriptor(id, URI.create(uri));
    }
}
