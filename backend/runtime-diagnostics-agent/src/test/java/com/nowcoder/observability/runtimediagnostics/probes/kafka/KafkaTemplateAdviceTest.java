package com.nowcoder.observability.runtimediagnostics.probes.kafka;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTemplateAdviceTest {

    @Test
    void topicIsHashedWhenTopicNamesAreDisabled() {
        assertThat(KafkaTemplateAdvice.destinationName("im-room-events", false))
                .hasSize(16)
                .doesNotContain("im-room-events");
    }

    @Test
    void topicCanBeEmittedWhenExplicitlyEnabled() {
        assertThat(KafkaTemplateAdvice.destinationName("im-room-events", true))
                .isEqualTo("im-room-events");
    }
}
