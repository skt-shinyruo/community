package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.common.kafka.trace.TraceKafkaSender;
import com.nowcoder.community.im.common.command.SendPrivateTextCommand;
import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class CommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String privateTextTopic;
    private final String roomTextTopic;

    public CommandProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${im.kafka.topics.command-private-text:im.command.private-text}") String privateTextTopic,
            @Value("${im.kafka.topics.command-room-text:im.command.room-text}") String roomTextTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.privateTextTopic = privateTextTopic;
        this.roomTextTopic = roomTextTopic;
    }

    public CompletableFuture<SendResult<String, Object>> sendPrivateText(SendPrivateTextCommand cmd) {
        if (cmd == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("command required"));
        }
        try {
            return TraceKafkaSender.send(kafkaTemplate, privateTextTopic, cmd.conversationId(), cmd);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendRoomText(SendRoomTextCommand cmd) {
        if (cmd == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("command required"));
        }
        try {
            return TraceKafkaSender.send(kafkaTemplate, roomTextTopic, String.valueOf(cmd.roomId()), cmd);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
