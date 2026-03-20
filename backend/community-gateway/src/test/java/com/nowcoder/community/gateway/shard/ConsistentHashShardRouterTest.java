package com.nowcoder.community.gateway.shard;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistentHashShardRouterTest {

    @Test
    void shouldReturnEmptyWhenNoHealthyWorkersRemain() {
        WorkerRegistry registry = new WorkerRegistry(new WorkerRegistryProperties());
        ShardRouter router = new ConsistentHashShardRouter(registry);

        assertThat(router.route("user-1")).isEmpty();
    }

    @Test
    void shouldRouteSameUserToSameWorkerWhileWorkerSetIsStable() {
        WorkerRegistry registry = registryWithThreeWorkers();

        ShardRouter firstRouter = new ConsistentHashShardRouter(registry);
        ShardRouter secondRouter = new ConsistentHashShardRouter(registry);

        Optional<WorkerDescriptor> firstDecision = firstRouter.route("user-42");
        Optional<WorkerDescriptor> secondDecision = firstRouter.route("user-42");
        Optional<WorkerDescriptor> thirdDecision = secondRouter.route("user-42");

        assertThat(firstDecision).isPresent();
        assertThat(secondDecision).isEqualTo(firstDecision);
        assertThat(thirdDecision).isEqualTo(firstDecision);
    }

    @Test
    void shouldExcludeUnhealthyWorkersFromRouting() {
        WorkerRegistry registry = registryWithThreeWorkers();
        registry.markUnhealthy("worker-b");

        ShardRouter router = new ConsistentHashShardRouter(registry);

        for (int i = 0; i < 200; i++) {
            assertThat(router.route("user-" + i))
                    .map(WorkerDescriptor::getId)
                    .hasValueSatisfying(id -> assertThat(id).isIn("worker-a", "worker-c"));
        }
    }

    @Test
    void shouldOnlyRemapUsersAssignedToRemovedWorker() {
        WorkerRegistry registry = registryWithThreeWorkers();
        ShardRouter router = new ConsistentHashShardRouter(registry);

        Map<String, String> before = new LinkedHashMap<>();
        for (int i = 0; i < 1000; i++) {
            before.put("user-" + i, routeToWorkerId(router, "user-" + i));
        }

        long originallyOnWorkerB = before.values().stream()
                .filter("worker-b"::equals)
                .count();
        assertThat(originallyOnWorkerB).isPositive();

        registry.markUnhealthy("worker-b");

        for (Map.Entry<String, String> entry : before.entrySet()) {
            String userId = entry.getKey();
            String previousWorkerId = entry.getValue();
            String nextWorkerId = routeToWorkerId(router, userId);

            if (!"worker-b".equals(previousWorkerId)) {
                assertThat(nextWorkerId).isEqualTo(previousWorkerId);
            } else {
                assertThat(nextWorkerId).isNotEqualTo("worker-b");
            }
        }
    }

    private static WorkerRegistry registryWithThreeWorkers() {
        WorkerRegistryProperties properties = new WorkerRegistryProperties();
        properties.getWorkers().add(worker("worker-a", "ws://127.0.0.1:18081/ws/im"));
        properties.getWorkers().add(worker("worker-b", "ws://127.0.0.1:18082/ws/im"));
        properties.getWorkers().add(worker("worker-c", "ws://127.0.0.1:18083/ws/im"));
        return new WorkerRegistry(properties);
    }

    private static WorkerRegistryProperties.Worker worker(String id, String uri) {
        WorkerRegistryProperties.Worker worker = new WorkerRegistryProperties.Worker();
        worker.setId(id);
        worker.setUri(java.net.URI.create(uri));
        return worker;
    }

    private static String routeToWorkerId(ShardRouter router, String userId) {
        return router.route(userId)
                .map(WorkerDescriptor::getId)
                .orElseThrow(() -> new AssertionError("Expected a worker for " + userId));
    }
}
