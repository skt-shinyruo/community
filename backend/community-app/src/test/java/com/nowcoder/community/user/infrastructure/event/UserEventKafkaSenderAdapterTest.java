package com.nowcoder.community.user.infrastructure.event;

import com.nowcoder.community.user.contracts.event.UserContractEvent;
import com.nowcoder.community.user.contracts.event.UserEventTypes;
import com.nowcoder.community.user.contracts.event.UserPolicyChangedPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserEventKafkaSenderAdapterTest {

    private static final String KAFKA_TOPIC = "user.events";

    @Test
    void senderShouldRemainKafkaClasspathConditional() {
        ConditionalOnClass conditionalOnClass = UserEventKafkaSenderAdapter.class.getAnnotation(ConditionalOnClass.class);

        assertThat(conditionalOnClass).isNotNull();
        assertThat(conditionalOnClass.value()).contains(KafkaTemplate.class);
    }

    @Test
    void dispatchShouldPublishContractEventWithConfiguredTopicAndKey() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        UserEventKafkaSenderAdapter adapter = new UserEventKafkaSenderAdapter(kafkaTemplate, KAFKA_TOPIC);
        UserPolicyChangedPayload payload = new UserPolicyChangedPayload();
        payload.setUserId(uuid(101));
        payload.setVersion(42L);
        UserContractEvent event = new UserContractEvent(
                "user:UserPolicyChanged:" + uuid(101) + ":42",
                UserEventTypes.USER_POLICY_CHANGED,
                payload
        );

        adapter.dispatch(uuid(101).toString(), event);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo(KAFKA_TOPIC);
        assertThat(record.key()).isEqualTo(uuid(101).toString());
        assertThat(record.value()).isSameAs(event);
    }

    @Test
    void dispatchFailureShouldWrapExceptionForOutboxRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        UserEventKafkaSenderAdapter adapter = new UserEventKafkaSenderAdapter(kafkaTemplate, KAFKA_TOPIC);

        assertThatThrownBy(() -> adapter.dispatch("key", new UserContractEvent("event-1", "Type", new Object())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user event kafka publish failed: " + KAFKA_TOPIC)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private static CompletableFuture<SendResult<String, Object>> completedSend() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private static CompletableFuture<SendResult<String, Object>> failedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        return future;
    }
}
