package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RealtimeWorkerDirectoryTest {

    @Test
    void resolvesWorkerInboxRoutingFromDiscoveryMetadataOnly() {
        RealtimeWorkerDirectory directory = directory(List.of(instance("worker-a", "63")));

        Optional<RealtimeWorkerEndpoint> result = directory.find("worker-a");

        assertThat(result).isPresent();
        RealtimeWorkerEndpoint endpoint = result.orElseThrow();
        assertThat(endpoint.workerId()).isEqualTo("worker-a");
        assertThat(endpoint.roomFanoutInboxSlot()).isEqualTo(63);
    }

    @Test
    void missingWorkerIdFailsClosed() {
        DefaultServiceInstance instance = instance(null, "5");

        assertThatThrownBy(() -> directory(List.of(instance)).find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("worker id");
    }

    @Test
    void missingRoomFanoutInboxSlotFailsClosed() {
        DefaultServiceInstance instance = instance("worker-a", null);

        assertThatThrownBy(() -> directory(List.of(instance)).find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout inbox slot");
    }

    @Test
    void malformedRoomFanoutInboxSlotFailsClosed() {
        DefaultServiceInstance instance = instance("worker-a", "not-a-number");

        assertThatThrownBy(() -> directory(List.of(instance)).find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout inbox slot");
    }

    @Test
    void negativeRoomFanoutInboxSlotFailsClosed() {
        DefaultServiceInstance instance = instance("worker-a", "-1");

        assertThatThrownBy(() -> directory(List.of(instance)).find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 63");
    }

    @Test
    void roomFanoutInboxSlotAtPartitionCountFailsClosed() {
        DefaultServiceInstance instance = instance("worker-a", "64");

        assertThatThrownBy(() -> directory(List.of(instance)).find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("between 0 and 63");
    }

    @Test
    void duplicateWorkerIdsFailClosed() {
        RealtimeWorkerDirectory directory = directory(List.of(
                instance("worker-a", "5"),
                instance("worker-a", "6")
        ));

        assertThatThrownBy(() -> directory.find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate realtime worker id");
    }

    @Test
    void duplicateRoomFanoutInboxSlotsFailClosed() {
        RealtimeWorkerDirectory directory = directory(List.of(
                instance("worker-a", "5"),
                instance("worker-b", "5")
        ));

        assertThatThrownBy(() -> directory.find("worker-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate room fanout inbox slot");
    }

    private static RealtimeWorkerDirectory directory(List<DefaultServiceInstance> instances) {
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setRoutedCommandPartitions(64);
        properties.setWorkerDirectoryCacheTtl(Duration.ZERO);
        ImSessionProperties sessionProperties = new ImSessionProperties();
        sessionProperties.setWorkerIdMetadataKey("workerId");
        return new RealtimeWorkerDirectory(() -> List.copyOf(instances), sessionProperties, properties);
    }

    private static DefaultServiceInstance instance(String workerId, String inboxSlot) {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                "ignored-instance-id",
                "im-realtime-worker",
                "",
                -1,
                false
        );
        if (workerId != null) {
            instance.getMetadata().put("workerId", workerId);
        }
        if (inboxSlot != null) {
            instance.getMetadata().put("roomFanoutInboxSlot", inboxSlot);
        }
        return instance;
    }
}
