package com.nowcoder.community.im.gateway.shard;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendezvousWorkerSelectorTest {

    @Test
    void shouldSelectSameWorkerForSameUserAndWorkerSet() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(List.of(
                new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18081/internal/ws/im")),
                new WorkerDescriptor("worker-b", URI.create("ws://127.0.0.1:18082/internal/ws/im")),
                new WorkerDescriptor("worker-c", URI.create("ws://127.0.0.1:18083/internal/ws/im"))
        )));
        UUID userId = UUID.fromString("00000000-0000-7000-8000-000000000123");

        WorkerDescriptor first = selector.select(userId);
        WorkerDescriptor second = selector.select(userId);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldReturnServiceUnavailableWhenNoHealthyWorkersExist() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(List.of()));

        assertThatThrownBy(() -> selector.select(UUID.fromString("00000000-0000-7000-8000-000000000123")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldIgnoreDrainingWorkersForHealthySelection() {
        WorkerRegistry registry = new WorkerRegistry(List.of(
                new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18081/internal/ws/im"),
                        true, 100, 0, "local"),
                new WorkerDescriptor("worker-b", URI.create("ws://127.0.0.1:18082/internal/ws/im"),
                        false, 100, 0, "local")
        ));

        assertThat(registry.healthyWorkers()).extracting(WorkerDescriptor::getId).containsExactly("worker-b");
    }

    @Test
    void shouldPreferAvailableCapacityOverHigherHashWhenWorkerIsFull() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(List.of(
                new WorkerDescriptor("worker-low", URI.create("ws://127.0.0.1:18081/internal/ws/im"),
                        false, 100, 100, "local"),
                new WorkerDescriptor("worker-full", URI.create("ws://127.0.0.1:18082/internal/ws/im"),
                        false, 100, 0, "local")
        )));

        WorkerDescriptor selected = selector.select(UUID.fromString("00000000-0000-7000-8000-000000000123"));

        assertThat(selected.getId()).isEqualTo("worker-full");
    }

    @Test
    void shouldReturnServiceUnavailableForRuntimeDuplicateWorkerIds() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(() -> List.of(
                new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18081/internal/ws/im")),
                new WorkerDescriptor("worker-a", URI.create("ws://127.0.0.1:18082/internal/ws/im"))
        )));

        assertThatThrownBy(() -> selector.select(UUID.fromString("00000000-0000-7000-8000-000000000123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("duplicate websocket worker id")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturnServiceUnavailableWhenWorkerDiscoveryFails() {
        RendezvousWorkerSelector selector = new RendezvousWorkerSelector(new WorkerRegistry(() -> {
            throw new IllegalStateException("discovery unavailable");
        }));

        assertThatThrownBy(() -> selector.select(UUID.fromString("00000000-0000-7000-8000-000000000123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("websocket worker discovery unavailable")
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
