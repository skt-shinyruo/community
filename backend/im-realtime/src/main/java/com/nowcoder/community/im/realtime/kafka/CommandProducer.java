package com.nowcoder.community.im.realtime.kafka;

import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.command.SendRoomTextCommandV1;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CommandProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPrivateText(SendPrivateTextCommandV1 cmd) {
        if (cmd == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.COMMAND_PRIVATE_TEXT_V1, cmd.conversationId(), cmd);
    }

    public void sendRoomText(SendRoomTextCommandV1 cmd) {
        if (cmd == null) {
            return;
        }
        kafkaTemplate.send(ImTopics.COMMAND_ROOM_TEXT_V1, String.valueOf(cmd.roomId()), cmd);
    }
}

