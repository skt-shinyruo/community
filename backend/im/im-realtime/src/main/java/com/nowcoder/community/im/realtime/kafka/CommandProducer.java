package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.command.SendRoomTextCommandV1;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class CommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CommandProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, Object>> sendPrivateText(SendPrivateTextCommandV1 cmd) {
        if (cmd == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("command required"));
        }
        try {
            return kafkaTemplate.send(ImTopics.COMMAND_PRIVATE_TEXT_V1, cmd.conversationId(), cmd);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<SendResult<String, Object>> sendRoomText(SendRoomTextCommandV1 cmd) {
        if (cmd == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("command required"));
        }
        try {
            return kafkaTemplate.send(ImTopics.COMMAND_ROOM_TEXT_V1, String.valueOf(cmd.roomId()), cmd);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
