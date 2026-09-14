package com.nowcoder.community.im.realtime.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaConfigProjectionDlqTest {

    @Test
    @SuppressWarnings("unchecked")
    void invalidProjectionEventShouldPublishToSourceTopicDlq() {
        KafkaConfig config = new KafkaConfig();
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                config::dlqDestination
        );

        List<String> projectionTopics = List.of(
                "im.event.room-member-changed",
                "im.event.user-messaging-policy-changed",
                "im.event.user-block-relation-changed"
        );
        for (String topic : projectionTopics) {
            clearInvocations(kafkaTemplate);
            recoverer.accept(
                    new ConsumerRecord<>(topic, 2, 42L, "key", "value"),
                    new IllegalArgumentException("malformed projection event")
            );

            ArgumentCaptor<ProducerRecord<Object, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());
            assertThat(captor.getValue().topic()).isEqualTo(topic + ".dlq");
            assertThat(captor.getValue().partition()).isEqualTo(2);
        }
    }
}
