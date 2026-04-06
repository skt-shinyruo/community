package com.nowcoder.community.gateway.shard;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveredWorkerDescriptorFactoryTest {

    @Test
    void shouldBuildWorkerDescriptorFromServiceInstanceMetadata() {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                "instance-1", "im-realtime-worker", "127.0.0.1", 18081, false
        );
        instance.getMetadata().put("workerId", "worker-a");
        instance.getMetadata().put("wsPath", "/internal/ws/im");
        instance.getMetadata().put("wsPort", "18081");

        WorkerDescriptor descriptor = new DiscoveredWorkerDescriptorFactory(new WorkerDiscoveryProperties())
                .from(instance)
                .orElseThrow();

        assertThat(descriptor.getId()).isEqualTo("worker-a");
        assertThat(descriptor.getUri()).isEqualTo(URI.create("ws://127.0.0.1:18081/internal/ws/im"));
    }
}
