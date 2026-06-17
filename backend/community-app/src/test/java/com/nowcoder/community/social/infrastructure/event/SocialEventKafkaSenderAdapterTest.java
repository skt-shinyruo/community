package com.nowcoder.community.social.infrastructure.event;

import com.nowcoder.community.common.constants.EntityTypes;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialContractEvent;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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

class SocialEventKafkaSenderAdapterTest {

    @Test
    void senderShouldOnlyLoadForSocialOutboxKafkaPublisher() {
        ConditionalOnClass conditionalOnClass = SocialEventKafkaSenderAdapter.class.getAnnotation(ConditionalOnClass.class);
        ConditionalOnExpression conditional = SocialEventKafkaSenderAdapter.class.getAnnotation(ConditionalOnExpression.class);

        assertThat(conditionalOnClass).isNotNull();
        assertThat(conditionalOnClass.value()).contains(KafkaTemplate.class);
        assertThat(conditional).isNotNull();
        assertThat(conditional.value()).isEqualTo(
                "'${social.events.publisher:outbox-kafka}' == 'outbox-kafka' && '${events.outbox.enabled:true}' == 'true'"
        );
    }

    @Test
    void sendShouldPublishContractEventWithTopicAndKey() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        SocialEventKafkaSenderAdapter adapter = new SocialEventKafkaSenderAdapter(kafkaTemplate);
        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(101));
        payload.setEntityType(EntityTypes.POST);
        payload.setEntityId(uuid(102));
        SocialContractEvent event = new SocialContractEvent(
                "social:LikeCreated:" + uuid(101) + ":" + EntityTypes.POST + ":" + uuid(102),
                SocialEventTypes.LIKE_CREATED,
                payload
        );

        adapter.send("social.events", uuid(101).toString(), event);

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo("social.events");
        assertThat(record.key()).isEqualTo(uuid(101).toString());
        assertThat(record.value()).isSameAs(event);
    }

    @Test
    void sendFailureShouldWrapExceptionForOutboxRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        SocialEventKafkaSenderAdapter adapter = new SocialEventKafkaSenderAdapter(kafkaTemplate);

        assertThatThrownBy(() -> adapter.send("social.events", "key", new SocialContractEvent("event-1", "Type", new Object())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("social event kafka publish failed: social.events")
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
