package com.nowcoder.community.content.infrastructure.event;

import com.nowcoder.community.content.contracts.event.ContentContractEvent;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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

class ContentEventKafkaSenderAdapterTest {

    @Test
    void senderShouldOnlyLoadForContentOutboxKafkaPublisher() {
        ConditionalOnExpression conditional = ContentEventKafkaSenderAdapter.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${content.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void sendShouldPublishContractEventWithTopicAndKey() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        ContentEventKafkaSenderAdapter adapter = new ContentEventKafkaSenderAdapter(kafkaTemplate);
        PostPayload payload = new PostPayload();
        payload.setPostId(uuid(101));
        ContentContractEvent event = new ContentContractEvent(
                "content:PostPublished:" + uuid(101),
                ContentEventTypes.POST_PUBLISHED,
                payload
        );

        adapter.send("content.events", uuid(101).toString(), event);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("content.events");
        assertThat(record.key()).isEqualTo(uuid(101).toString());
        assertThat(record.value()).isSameAs(event);
    }

    @Test
    void sendFailureShouldWrapExceptionForOutboxRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        ContentEventKafkaSenderAdapter adapter = new ContentEventKafkaSenderAdapter(kafkaTemplate);

        assertThatThrownBy(() -> adapter.send("content.events", "key", new ContentContractEvent("event-1", "Type", new Object())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("content event kafka publish failed: content.events")
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
