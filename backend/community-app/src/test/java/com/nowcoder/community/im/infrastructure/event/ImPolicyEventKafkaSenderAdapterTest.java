package com.nowcoder.community.im.infrastructure.event;

import com.nowcoder.community.common.kafka.trace.TraceKafkaHeaders;
import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.im.common.event.UserMessagingPolicyChanged;
import io.opentelemetry.api.trace.SpanKind;
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

class ImPolicyEventKafkaSenderAdapterTest {

    private static final String USER_POLICY_TOPIC = "im.policy.user";
    private static final String BLOCK_TOPIC = "im.policy.block";

    @Test
    void senderShouldRemainKafkaClasspathConditional() {
        ConditionalOnClass conditionalOnClass = ImPolicyEventKafkaSenderAdapter.class.getAnnotation(ConditionalOnClass.class);

        assertThat(conditionalOnClass).isNotNull();
        assertThat(conditionalOnClass.value()).containsExactly(KafkaTemplate.class);
    }

    @Test
    void sendShouldPublishWithTopicKeyPayloadAndTraceHeaders() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        ImPolicyEventKafkaSenderAdapter adapter = new ImPolicyEventKafkaSenderAdapter(
                kafkaTemplate,
                USER_POLICY_TOPIC,
                BLOCK_TOPIC
        );
        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee",
                "00-eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee-00f067aa0ba902b7-01"
        );
        UserMessagingPolicyChanged event = new UserMessagingPolicyChanged("evt-policy-1", uuid(7), true, false, false,
                null, null, true, 1712345678901L, 1L);

        try (var ignored = OtelTraceContext.openForInbound(snapshot.traceparent(), "outbox.process im", SpanKind.CONSUMER)) {
            adapter.dispatchUserMessagingPolicyChanged(uuid(7).toString(), event);
        }

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo(USER_POLICY_TOPIC);
        assertThat(recordCaptor.getValue().key()).isEqualTo(uuid(7).toString());
        assertThat(recordCaptor.getValue().value()).isSameAs(event);
        assertThat(TraceKafkaHeaders.headerValue(recordCaptor.getValue().headers(), TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo(snapshot.traceparent());
    }

    @Test
    void sendFailureShouldWrapExceptionForOutboxRetry() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        ImPolicyEventKafkaSenderAdapter adapter = new ImPolicyEventKafkaSenderAdapter(
                kafkaTemplate,
                USER_POLICY_TOPIC,
                BLOCK_TOPIC
        );

        assertThatThrownBy(() -> adapter.dispatchUserMessagingPolicyChanged("key-1", new UserMessagingPolicyChanged(
                "evt",
                uuid(1),
                true,
                false,
                false,
                null,
                null,
                true,
                1L,
                1L
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("im policy kafka publish failed: " + USER_POLICY_TOPIC)
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
