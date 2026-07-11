package com.nowcoder.community.im.realtime.fanout;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;

class RoomFanoutConfigurationTest {

    @Test
    void routedOwnerTargetAndKafkaDispatcherAreUnconditional() {
        assertThat(RoomPersistedOwnerConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(RoomFanoutTargetConsumer.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(KafkaRoomFanoutDispatcher.class.getAnnotation(ConditionalOnExpression.class)).isNull();
        assertThat(KafkaRoomFanoutDispatcher.class.getAnnotation(Primary.class)).isNull();
    }
}
