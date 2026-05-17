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
                .from(instanceWithPort("18081"))
                .orElseThrow();

        assertThat(descriptor.getId()).isEqualTo("worker-a");
        assertThat(descriptor.getUri()).isEqualTo(URI.create("ws://127.0.0.1:18081/internal/ws/im"));
        assertThat(descriptor.isDraining()).isFalse();
        assertThat(descriptor.getMaxConnections()).isEqualTo(100);
        assertThat(descriptor.getActiveConnectionHint()).isEqualTo(25);
        assertThat(descriptor.getShardGroup()).isEqualTo("local-a");
    }

    @Test
    void shouldIgnoreMalformedWorkerPorts() {
        assertThat(new DiscoveredWorkerDescriptorFactory(new ImGatewaySessionProperties())
                .from(instanceWithPort("not-a-port")))
                .isEmpty();
    }

    @Test
    void shouldIgnoreOutOfRangeWorkerPorts() {
        DiscoveredWorkerDescriptorFactory factory = new DiscoveredWorkerDescriptorFactory(new ImGatewaySessionProperties());

        assertThat(factory.from(instanceWithPort("0"))).isEmpty();
        assertThat(factory.from(instanceWithPort("65536"))).isEmpty();
    }

    private static DefaultServiceInstance instanceWithPort(String wsPort) {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                "instance-1", "im-realtime-worker", "127.0.0.1", 18081, false
        );
        instance.getMetadata().put("workerId", "worker-a");
        instance.getMetadata().put("wsPath", "/internal/ws/im");
        instance.getMetadata().put("wsPort", wsPort);
        instance.getMetadata().put("draining", "false");
        instance.getMetadata().put("maxConnections", "100");
        instance.getMetadata().put("activeConnectionHint", "25");
        instance.getMetadata().put("shardGroup", "local-a");
        return instance;
    }
}
