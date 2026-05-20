package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.realtime.session.ImSessionProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaRoomFanoutDispatcherTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishesCommandToTargetInboxPartitionAndWaitsForBrokerAck() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        RoomFanoutProperties properties = properties();
        RealtimeWorkerDirectory directory = directory(properties, List.of(instance("worker-a", 5)));
        KafkaRoomFanoutDispatcher dispatcher = new KafkaRoomFanoutDispatcher(
                kafkaTemplate,
                directory,
                properties,
                sessionProperties("owner-worker")
        );
        RoomFanoutCommand command = command("worker-a");

        dispatcher.dispatch(command);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<ProducerRecord<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("im.command.room-fanout-routed");
        assertThat(record.partition()).isEqualTo(5);
        assertThat(record.key()).isEqualTo("worker-a");
        assertThat(record.value()).isSameAs(command);
    }

    @Test
    @SuppressWarnings("unchecked")
    void localTargetStillPublishesThroughDurableKafkaInbox() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        RoomFanoutProperties properties = properties();
        properties.setWorkerInboxSlot(7);
        KafkaRoomFanoutDispatcher dispatcher = new KafkaRoomFanoutDispatcher(
                kafkaTemplate,
                directory(properties, List.of()),
                properties,
                sessionProperties("worker-a")
        );
        RoomFanoutCommand command = command("worker-a");

        dispatcher.dispatch(command);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<ProducerRecord<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().partition()).isEqualTo(7);
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingRemoteInboxSlotFailsBeforeOwnerOffsetCanCommit() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        RoomFanoutProperties properties = properties();
        RealtimeWorkerDirectory directory = directory(properties, List.of(instanceWithoutInboxSlot("worker-a")));
        KafkaRoomFanoutDispatcher dispatcher = new KafkaRoomFanoutDispatcher(
                kafkaTemplate,
                directory,
                properties,
                sessionProperties("owner-worker")
        );

        assertThatThrownBy(() -> dispatcher.dispatch(command("worker-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("room fanout inbox slot");
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    private static RoomFanoutProperties properties() {
        RoomFanoutProperties properties = new RoomFanoutProperties();
        properties.setRoutedCommandTopic("im.command.room-fanout-routed");
        properties.setRoutedCommandPartitions(16);
        properties.setTargetTimeout(Duration.ofSeconds(1));
        properties.setWorkerDirectoryCacheTtl(Duration.ZERO);
        return properties;
    }

    private static RealtimeWorkerDirectory directory(
            RoomFanoutProperties properties,
            List<DefaultServiceInstance> instances
    ) {
        ImSessionProperties sessionProperties = new ImSessionProperties();
        sessionProperties.setWorkerIdMetadataKey("workerId");
        return new RealtimeWorkerDirectory(() -> List.copyOf(instances), sessionProperties, properties);
    }

    private static ImSessionProperties sessionProperties(String workerId) {
        ImSessionProperties sessionProperties = new ImSessionProperties();
        sessionProperties.setWorkerId(workerId);
        return sessionProperties;
    }

    private static RoomFanoutCommand command(String workerId) {
        return new RoomFanoutCommand(
                workerId,
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                42L,
                "evt-1",
                1000L
        );
    }

    private static DefaultServiceInstance instance(String workerId, int inboxSlot) {
        DefaultServiceInstance instance = instanceWithoutInboxSlot(workerId);
        instance.getMetadata().put("roomFanoutInboxSlot", String.valueOf(inboxSlot));
        return instance;
    }

    private static DefaultServiceInstance instanceWithoutInboxSlot(String workerId) {
        DefaultServiceInstance instance = new DefaultServiceInstance(
                workerId + "-instance",
                "im-realtime-worker",
                "10.0.0.8",
                18081,
                false
        );
        instance.getMetadata().put("workerId", workerId);
        instance.getMetadata().put("protocol", "http");
        return instance;
    }
}
