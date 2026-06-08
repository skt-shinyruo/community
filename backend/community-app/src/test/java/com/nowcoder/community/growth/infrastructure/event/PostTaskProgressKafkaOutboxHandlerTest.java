package com.nowcoder.community.growth.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nowcoder.community.common.json.JacksonJsonCodec;
import com.nowcoder.community.common.json.JsonMappers;
import com.nowcoder.community.common.outbox.OutboxEvent;
import com.nowcoder.community.content.contracts.event.PostPayload;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PostTaskProgressKafkaOutboxHandlerTest {

    private static final String OUTBOX_TOPIC = "custom.projection.growth.task.post";
    private static final String KAFKA_TOPIC = "custom.growth.task.post-published";

    @Test
    void handlerShouldPublishPostTaskProgressEventToKafka() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(completedSend());
        PostTaskProgressKafkaOutboxHandler handler =
                new PostTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);
        UUID postId = uuid(100);
        UUID userId = uuid(7);
        Instant createTime = Instant.parse("2026-05-18T08:30:00Z");

        handler.handle(outboxEvent(objectMapper, postId, userId, createTime));

        ArgumentCaptor<ProducerRecord<String, Object>> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, Object> record = recordCaptor.getValue();
        assertThat(record.topic()).isEqualTo(KAFKA_TOPIC);
        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value()).isInstanceOf(PostPayload.class);
        PostPayload published = (PostPayload) record.value();
        assertThat(published.getPostId()).isEqualTo(postId);
        assertThat(published.getUserId()).isEqualTo(userId);
        assertThat(published.getCreateTime()).isEqualTo(createTime);
    }

    @Test
    void handlerShouldIgnoreBlankPayload() {
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        PostTaskProgressKafkaOutboxHandler handler =
                new PostTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);

        handler.handle(new OutboxEvent(UUID.randomUUID(), "event-id", OUTBOX_TOPIC, "key", " ", "PENDING", 0, null, null, null, null));

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void kafkaPublishFailureShouldFailOutboxHandlingForRetry() throws Exception {
        ObjectMapper objectMapper = JsonMappers.standard();
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend());
        PostTaskProgressKafkaOutboxHandler handler =
                new PostTaskProgressKafkaOutboxHandler(new JacksonJsonCodec(JsonMappers.standard()), kafkaTemplate, OUTBOX_TOPIC, KAFKA_TOPIC);

        assertThatThrownBy(() -> handler.handle(outboxEvent(objectMapper, uuid(100), uuid(7), Instant.parse("2026-05-18T08:30:00Z"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("growth task post kafka publish failed");
    }

    private static OutboxEvent outboxEvent(ObjectMapper objectMapper, UUID postId, UUID userId, Instant createTime) throws Exception {
        PostPayload payload = new PostPayload();
        payload.setPostId(postId);
        payload.setUserId(userId);
        payload.setCreateTime(createTime);
        return new OutboxEvent(
                UUID.fromString("01965429-b34a-7000-8000-000000000052"),
                "post-published:" + postId + ":growth_task",
                OUTBOX_TOPIC,
                userId.toString(),
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
