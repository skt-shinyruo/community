package com.nowcoder.community.growth.infrastructure.event;

import com.nowcoder.community.content.contracts.event.PostPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskProgressKafkaSenderAdapterTest {

    @Test
    void senderShouldOnlyLoadWhenKafkaIsPresent() {
        ConditionalOnClass conditional = TaskProgressKafkaSenderAdapter.class.getAnnotation(ConditionalOnClass.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).containsExactly(KafkaTemplate.class);
    }

    @Test
    void sendShouldPublishTopicKeyAndPayload() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        TaskProgressKafkaSenderAdapter adapter = new TaskProgressKafkaSenderAdapter(kafkaTemplate);
        PostPayload payload = new PostPayload();

        adapter.send("growth.topic", "key-1", payload);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo("growth.topic");
        assertThat(recordCaptor.getValue().key()).isEqualTo("key-1");
        assertThat(recordCaptor.getValue().value()).isSameAs(payload);
    }

    @Test
    void sendFailureShouldWrapExceptionForOutboxRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        TaskProgressKafkaSenderAdapter adapter = new TaskProgressKafkaSenderAdapter(kafkaTemplate);

        assertThatThrownBy(() -> adapter.send("growth.topic", "key-1", new PostPayload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("growth task kafka publish failed: growth.topic")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private CompletableFuture<SendResult<String, Object>> completedSend() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, Object>> failedSend() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("kafka down"));
        return future;
    }
}
