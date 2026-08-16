package com.nowcoder.community.im.gateway.shard;

import com.nowcoder.community.im.gateway.session.ImGatewaySessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveredWorkerDescriptorFactoryTest {

    @Test
    void shouldBuildWorkerDescriptorFromServiceInstanceMetadata() {
        WorkerDescriptor descriptor = new DiscoveredWorkerDescriptorFactory(new ImGatewaySessionProperties())
                .from(instanceWithPort(18081))
                .orElseThrow();

        assertThat(descriptor.id()).isEqualTo("worker-a");
        assertThat(descriptor.uri()).isEqualTo(URI.create("ws://127.0.0.1:18081/internal/ws/im"));
        assertThat(descriptor.draining()).isFalse();
        assertThat(descriptor.maxConnections()).isEqualTo(100);
        assertThat(descriptor.activeConnectionHint()).isEqualTo(25);
    }

    @Test
    void shouldIgnoreOutOfRangeWorkerPorts() {
        DiscoveredWorkerDescriptorFactory factory = new DiscoveredWorkerDescriptorFactory(new ImGatewaySessionProperties());

        assertThat(factory.from(instanceWithPort(0))).isEmpty();
        assertThat(factory.from(instanceWithPort(65536))).isEmpty();
    }

    @Test
    void shouldDefaultInvalidConnectionMetadataToZero() {
        DefaultServiceInstance instance = instanceWithPort(18081);
        instance.getMetadata().put("maxConnections", "-1");
        instance.getMetadata().put("activeConnectionHint", "2147483648");

        WorkerDescriptor descriptor = new DiscoveredWorkerDescriptorFactory(new ImGatewaySessionProperties())
                .from(instance)
                .orElseThrow();

        assertThat(descriptor.maxConnections()).isZero();
        assertThat(descriptor.activeConnectionHint()).isZero();
    }

    private static DefaultServiceInstance instanceWithPort(int port) {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                "instance-1", "im-realtime-worker", "127.0.0.1", port, false
        );
        instance.getMetadata().put("workerId", "worker-a");
        instance.getMetadata().put("draining", "false");
        instance.getMetadata().put("maxConnections", "100");
        instance.getMetadata().put("activeConnectionHint", "25");
        return instance;
    }
}
