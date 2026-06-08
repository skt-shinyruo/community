package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.social.contracts.event.LikePayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.nowcoder.community.common.constants.EntityTypes.POST;
import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LikeTaskProgressKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.projection.growth.task.like";
    private static final String KAFKA_TOPIC = "custom.growth.task.like-created";

    @Test
    void handlerShouldPublishLikeTaskProgressEventToKafka() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        LikeTaskProgressKafkaOutboxHandler handler =
                new LikeTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);
        UUID actorUserId = uuid(1);
        UUID entityId = uuid(100);
        UUID entityUserId = uuid(2);
        Instant createTime = Instant.parse("2026-05-18T10:30:00Z");

        handler.handle(outboxEvent(objectMapper, actorUserId, entityId, entityUserId, createTime));

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo(KAFKA_TOPIC);
        assertThat(record.key()).isEqualTo(entityUserId.toString());
        assertThat(record.value()).isInstanceOf(LikePayload.class);
        LikePayload published = (LikePayload) record.value();
        assertThat(published.getActorUserId()).isEqualTo(actorUserId);
        assertThat(published.getEntityId()).isEqualTo(entityId);
        assertThat(published.getEntityUserId()).isEqualTo(entityUserId);
        assertThat(published.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        LikeTaskProgressKafkaOutboxHandler handler =
                new LikeTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);

        handler.handle(new OutboxEvent(UUID.randomUUID(), "event-id", OUTBOX_TOPIC, "key", " ", "PENDING", 0, null, null, null, null));

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void kafkaPublishFailureShouldFailOutboxHandlingForRetry() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        LikeTaskProgressKafkaOutboxHandler handler =
                new LikeTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);

        assertThatThrownBy(() -> handler.handle(outboxEvent(
                objectMapper,
                uuid(1),
                uuid(100),
                uuid(2),
                Instant.parse("2026-05-18T10:30:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("growth task like kafka publish failed");
    }

    private static OutboxEvent outboxEvent(
            ObjectMapper objectMapper,
            UUID actorUserId,
            UUID entityId,
            UUID entityUserId,
            Instant createTime
    ) throws Exception {
        LikePayload payload = new LikePayload();
        payload.setActorUserId(actorUserId);
        payload.setEntityType(POST);
        payload.setEntityId(entityId);
        payload.setEntityUserId(entityUserId);
        payload.setCreateTime(createTime);
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000062"),
                "like-created:" + actorUserId + ":" + POST + ":" + entityId + ":growth_task",
                OUTBOX_TOPIC,
                entityUserId.toString(),
                objectMapper.writeValueAsString(payload),
                "PENDING",
                0,
                null,
                null,
                null,
                null
        );
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
