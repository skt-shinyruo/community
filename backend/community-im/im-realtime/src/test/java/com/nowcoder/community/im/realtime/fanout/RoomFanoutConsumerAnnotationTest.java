package com.nowcoder.community.im.realtime.fanout;

import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
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
}
