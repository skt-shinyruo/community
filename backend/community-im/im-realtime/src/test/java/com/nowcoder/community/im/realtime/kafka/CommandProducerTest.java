package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.common.trace.TraceContext;
import com.nowcoder.community.common.trace.TraceHeaders;
import com.nowcoder.community.im.common.ImTopics;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandProducerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void sendPrivateTextShouldAttachTraceHeaders() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        TraceContext.set("abababababababababababababababab");
        CommandProducer producer = new CommandProducer(kafkaTemplate);
        SendPrivateTextCommand command = new SendPrivateTextCommand(
                "req-1",
                "client-1",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "conv-1",
                "hello",
                1L
        );

        producer.sendPrivateText(command);

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(ImTopics.COMMAND_PRIVATE_TEXT);
        assertThat(record.key()).isEqualTo("conv-1");
        assertThat(record.value()).isEqualTo(command);
        assertThat(new String(record.headers().lastHeader(TraceHeaders.HEADER_TRACEPARENT).value(), StandardCharsets.UTF_8))
                .startsWith("00-abababababababababababababababab-");
    }

    @Test
    void sendRoomTextShouldAttachTraceHeaders() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        TraceContext.set("bcbcbcbcbcbcbcbcbcbcbcbcbcbcbcbc");
        CommandProducer producer = new CommandProducer(kafkaTemplate);
        UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        SendRoomTextCommand command = new SendRoomTextCommand(
                "req-2",
                "client-2",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                roomId,
                "hello room",
                2L
        );

        producer.sendRoomText(command);

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, Object> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(ImTopics.COMMAND_ROOM_TEXT);
        assertThat(record.key()).isEqualTo(String.valueOf(roomId));
        assertThat(record.value()).isEqualTo(command);
        assertThat(new String(record.headers().lastHeader(TraceHeaders.HEADER_TRACEPARENT).value(), StandardCharsets.UTF_8))
                .startsWith("00-bcbcbcbcbcbcbcbcbcbcbcbcbcbcbcbc-");
    }
}
