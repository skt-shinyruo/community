package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImKafkaOutboxHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handleSendsDeserializedPayloadToKafkaAndWaitsForSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq(ImTopics.EVENT_PRIVATE_PERSISTED), eq("conv-1"), any(PrivateMessagePersistedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                "conv-1",
                7L,
                uuid(7),
                uuid(1),
                uuid(2),
                "hello",
                "req-1",
                "c1",
                123L
        );
        ImKafkaOutboxHandler<PrivateMessagePersistedEvent> handler = new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );

        handler.handle(outboxEvent("req-1:private_persisted", "conv-1", objectMapper.writeValueAsString(event)));

        ArgumentCaptor<PrivateMessagePersistedEvent> payloadCaptor = ArgumentCaptor.forClass(PrivateMessagePersistedEvent.class);
        verify(kafkaTemplate).send(eq(ImTopics.EVENT_PRIVATE_PERSISTED), eq("conv-1"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().messageId()).isEqualTo(uuid(7));
        assertThat(payloadCaptor.getValue().requestId()).isEqualTo("req-1");
    }

    @Test
    void handleThrowsWhenKafkaSendFailsSoOutboxCanRetry() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(eq(ImTopics.EVENT_PRIVATE_PERSISTED), eq("conv-1"), any(PrivateMessagePersistedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                "conv-1",
                7L,
                uuid(7),
                uuid(1),
                uuid(2),
                "hello",
                "req-1",
                "c1",
                123L
        );
        ImKafkaOutboxHandler<PrivateMessagePersistedEvent> handler = new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                objectMapper,
                kafkaTemplate
        );

        assertThatThrownBy(() -> handler.handle(outboxEvent(
                "req-1:private_persisted",
                "conv-1",
                objectMapper.writeValueAsString(event)
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM outbox kafka publish failed");
    }

    private OutboxEvent outboxEvent(String eventId, String eventKey, String payload) {
        return new OutboxEvent(
                uuid(99),
                eventId,
                ImTopics.EVENT_PRIVATE_PERSISTED,
                eventKey,
                payload,
                OutboxEventStatus.PENDING,
                0,
                null,
                null,
                null,
                null
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
