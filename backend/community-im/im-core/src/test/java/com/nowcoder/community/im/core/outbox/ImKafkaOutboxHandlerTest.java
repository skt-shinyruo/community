package com.nowcoder.community.im.core.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonCodec;
import com.nowcoder.community.common.json.JsonCodecException;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.kafka.trace.TraceKafkaHeaders;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.common.outbox.OutboxEventStatus;
import com.nowcoder.community.common.trace.OtelTraceContext;
import com.nowcoder.community.common.trace.TraceContextSnapshot;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.event.PrivateMessagePersistedEvent;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImKafkaOutboxHandlerTest {

    private final ObjectMapper objectMapper = JsonMappers.standard();

    @Test
    void handleSendsDeserializedPayloadToKafkaAndWaitsForSuccess() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                "conv-1",
                7L,
                uuid(7),
                uuid(1),
                uuid(2),
                "hello",
                123L
        );
        ImKafkaOutboxHandler<PrivateMessagePersistedEvent> handler = new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                new JacksonJsonCodec(JsonMappers.standard()),
                kafkaTemplate
        );

        OutboxEvent outboxEvent = outboxEvent(
                "im:pf:" + uuid(7),
                "conv-1",
                objectMapper.writeValueAsString(event),
                "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd",
                "00-cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd-00f067aa0ba902b7-01"
        );

        TraceContextSnapshot snapshot = TraceContextSnapshot.fromStored(outboxEvent.traceId(), outboxEvent.traceparent());
        try (var ignored = OtelTraceContext.openForInbound(
                snapshot.traceparent(),
                "outbox.process " + outboxEvent.topic(),
                SpanKind.CONSUMER
        )) {
            handler.handle(outboxEvent);
        }

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo(ImTopics.EVENT_PRIVATE_PERSISTED);
        assertThat(record.key()).isEqualTo("conv-1");
        assertThat(record.value()).isInstanceOf(PrivateMessagePersistedEvent.class);
        PrivateMessagePersistedEvent published = (PrivateMessagePersistedEvent) record.value();
        assertThat(published.messageId()).isEqualTo(uuid(7));
        assertThat(TraceKafkaHeaders.headerValue(record.headers(), TraceHeaders.HEADER_TRACEPARENT))
                .isEqualTo(snapshot.traceparent());
    }

    @Test
    void handleThrowsWhenKafkaSendFailsSoOutboxCanRetry() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        PrivateMessagePersistedEvent event = new PrivateMessagePersistedEvent(
                "evt-1",
                "conv-1",
                7L,
                uuid(7),
                uuid(1),
                uuid(2),
                "hello",
                123L
        );
        ImKafkaOutboxHandler<PrivateMessagePersistedEvent> handler = new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                new JacksonJsonCodec(JsonMappers.standard()),
                kafkaTemplate
        );

        assertThatThrownBy(() -> handler.handle(outboxEvent(
                "im:pf:" + uuid(7),
                "conv-1",
                objectMapper.writeValueAsString(event),
                null,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IM outbox kafka publish failed");
    }

    @Test
    void handleWrapsJsonCodecDeserializationFailure() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        JsonCodec jsonCodec = mock(JsonCodec.class);
        when(jsonCodec.fromJson(any(String.class), any(Class.class)))
                .thenThrow(new JsonCodecException("deserialize json failed", new IllegalArgumentException("bad payload")));
        ImKafkaOutboxHandler<PrivateMessagePersistedEvent> handler = new ImKafkaOutboxHandler<>(
                ImTopics.EVENT_PRIVATE_PERSISTED,
                PrivateMessagePersistedEvent.class,
                jsonCodec,
                kafkaTemplate
        );

        assertThatThrownBy(() -> handler.handle(outboxEvent(
                "im:pf:" + uuid(8),
                "conv-1",
                "{",
                null,
                null
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("IM outbox payload deserialization failed: " + ImTopics.EVENT_PRIVATE_PERSISTED)
                .hasCauseInstanceOf(JsonCodecException.class);
    }

    private OutboxEvent outboxEvent(String eventId, String eventKey, String payload, String traceId, String traceparent) {
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
                traceId,
                traceparent
        );
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", suffix));
    }
}
