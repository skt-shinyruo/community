package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.command.RoomFanoutCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;

import static org.assertj.core.api.Assertions.assertThat;

class RoomFanoutConsumerAnnotationTest {

    @Test
    void ownerConsumerUsesSharedOwnerGroupWithoutConditionalActivation() throws Exception {
        KafkaListener listener = RoomPersistedOwnerConsumer.class
                .getDeclaredMethod("onRoomPersisted", RoomMessagePersistedEvent.class)
                .getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topics()).containsExactly("${im.kafka.topics.event-room-persisted:im.event.room-persisted}");
        assertThat(listener.groupId()).isEqualTo("${im.room-fanout.owner-group-id:im-realtime-room-fanout-owner}");
        assertThat(listener.concurrency())
                .isEqualTo("${im.room-fanout.owner-concurrency:${im.kafka.event.concurrency:3}}");
        assertThat(RoomPersistedOwnerConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
    }

    @Test
    void targetConsumerUsesRequiredWorkerInboxPartitionWithoutConditionalActivation() throws Exception {
        KafkaListener listener = RoomFanoutTargetConsumer.class
                .getDeclaredMethod("onCommand", RoomFanoutCommand.class)
                .getAnnotation(KafkaListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.topicPartitions()).singleElement().satisfies(topicPartition -> {
            assertThat(topicPartition.topic())
                    .isEqualTo("${im.room-fanout.routed-command-topic:im.command.room-fanout-routed}");
            assertThat(topicPartition.partitions()).containsExactly("${im.room-fanout.worker-inbox-slot}");
        });
        assertThat(listener.groupId()).isEqualTo("${im.room-fanout.target-group-id:im-realtime-room-fanout-target}");
        assertThat(RoomFanoutTargetConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
    }
}
