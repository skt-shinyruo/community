package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeWorkerDirectoryTest {

    @Test
    void resolvesTargetWorkerUriFromDiscoveryMetadata() {
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setTargetPath("/internal/im/realtime/fanout/room");
        properties.setWorkerDirectoryCacheTtl(Duration.ZERO);
        ImSessionProperties sessionProperties = new ImSessionProperties();
        sessionProperties.setWorkerServiceId("im-realtime-worker");
        sessionProperties.setWorkerIdMetadataKey("workerId");
        RealtimeWorkerDirectory directory = new RealtimeWorkerDirectory(
                () -> List.of(instance("worker-a", "10.0.0.8", 18081)),
                sessionProperties,
                properties
        );

        RealtimeWorkerEndpoint endpoint = directory.find("worker-a").orElseThrow();

        assertThat(endpoint.workerId()).isEqualTo("worker-a");
        assertThat(endpoint.uri().toString()).isEqualTo("http://10.0.0.8:18081/internal/im/realtime/fanout/room");
    }

    @Test
    void duplicateWorkerIdsFailClosed() {
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setWorkerDirectoryCacheTtl(Duration.ZERO);
        ImSessionProperties sessionProperties = new ImSessionProperties();
        sessionProperties.setWorkerIdMetadataKey("workerId");
        RealtimeWorkerDirectory directory = new RealtimeWorkerDirectory(
                () -> List.of(
                        instance("worker-a", "10.0.0.8", 18081),
                        instance("worker-a", "10.0.0.9", 18082)
                ),
                sessionProperties,
                properties
        );

        assertThatThrownBy(() -> directory.find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate realtime worker id");
    }

    private static DefaultServiceInstance instance(String workerId, String host, int port) {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                workerId + "-instance",
                "im-realtime-worker",
                host,
                port,
                false
        );
        instance.getMetadata().put("workerId", workerId);
        instance.getMetadata().put("protocol", "http");
        return instance;
    }
}
