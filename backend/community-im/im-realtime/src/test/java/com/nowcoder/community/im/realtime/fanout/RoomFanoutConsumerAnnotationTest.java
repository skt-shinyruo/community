package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;

import static org.assertj.core.api.Assertions.assertThat;

class RoomFanoutConsumerAnnotationTest {

    @Test
    void ownerConsumerUsesSharedRoomFanoutOwnerGroup() throws Exception {
        KafkaListener listener = RoomPersistedOwnerConsumer.class
                .getDeclaredMethod("onRoomPersisted", RoomMessagePersistedEvent.class)
                .getAnnotation(KafkaListener.class);
        ConditionalOnExpression condition = RoomPersistedOwnerConsumer.class
                .getAnnotation(ConditionalOnExpression.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topics()).containsExactly("${im.kafka.topics.event-room-persisted:im.event.room-persisted}");
        assertThat(listener.groupId()).isEqualTo("${im.room-fanout.owner-group-id:im-realtime-room-fanout-owner}");
        assertThat(condition.value()).contains("shadow").contains("routed");
    }

    @Test
    void legacyConsumerRemainsAvailableForLegacyAndShadowRollback() throws Exception {
        KafkaListener listener = RoomPersistedLegacyConsumer.class
                .getDeclaredMethod("onRoomPersisted", RoomMessagePersistedEvent.class)
                .getAnnotation(KafkaListener.class);
        ConditionalOnExpression condition = RoomPersistedLegacyConsumer.class
                .getAnnotation(ConditionalOnExpression.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topics()).containsExactly("${im.kafka.topics.event-room-persisted:im.event.room-persisted}");
        assertThat(listener.groupId()).isEmpty();
        assertThat(condition.value()).contains("legacy").contains("shadow");
    }

    @Test
    void targetConsumerUsesFixedWorkerInboxPartitionOnlyInKafkaRoutedMode() throws Exception {
        KafkaListener listener = RoomFanoutTargetConsumer.class
                .getDeclaredMethod("onCommand", RoomFanoutCommand.class)
                .getAnnotation(KafkaListener.class);
        ConditionalOnExpression condition = RoomFanoutTargetConsumer.class
                .getAnnotation(ConditionalOnExpression.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topicPartitions()).singleElement().satisfies(topicPartition -> {
            assertThat(topicPartition.topic()).isEqualTo("${im.room-fanout.routed-command-topic:im.command.room-fanout-routed}");
            assertThat(topicPartition.partitions()).containsExactly("${im.room-fanout.worker-inbox-slot:0}");
        });
        assertThat(listener.groupId()).isEqualTo("${im.room-fanout.target-group-id:im-realtime-room-fanout-target}");
        assertThat(condition.value()).contains("routed").contains("kafka");
    }
}
