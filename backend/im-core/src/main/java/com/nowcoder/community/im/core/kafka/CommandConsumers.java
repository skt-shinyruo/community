package com.nowcoder.community.im.core.kafka;

import com.nowcoder.community.im.contracts.ImTopics;
import com.nowcoder.community.im.contracts.command.SendPrivateTextCommandV1;
import com.nowcoder.community.im.contracts.command.SendRoomTextCommandV1;
import com.nowcoder.community.im.core.service.PrivateMessageService;
import com.nowcoder.community.im.core.service.RoomMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CommandConsumers {

    private static final Logger log = LoggerFactory.getLogger(CommandConsumers.class);

    private final PrivateMessageService privateMessageService;
    private final RoomMessageService roomMessageService;
    private final EventProducer eventProducer;

    public CommandConsumers(
            PrivateMessageService privateMessageService,
            RoomMessageService roomMessageService,
            EventProducer eventProducer
    ) {
        this.privateMessageService = privateMessageService;
        this.roomMessageService = roomMessageService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = ImTopics.COMMAND_PRIVATE_TEXT_V1, containerFactory = "kafkaListenerContainerFactory")
    public void onPrivateText(SendPrivateTextCommandV1 cmd) {
        if (cmd == null) {
            return;
        }
        var event = privateMessageService.persist(cmd);
        eventProducer.publishPrivatePersisted(event);
        log.debug("[im-core] persisted private (conversationId={}, seq={}, messageId={})",
                event.conversationId(), event.seq(), event.messageId());
    }

    @KafkaListener(topics = ImTopics.COMMAND_ROOM_TEXT_V1, containerFactory = "kafkaListenerContainerFactory")
    public void onRoomText(SendRoomTextCommandV1 cmd) {
        if (cmd == null) {
            return;
        }
        var event = roomMessageService.persist(cmd);
        eventProducer.publishRoomPersisted(event);
        log.debug("[im-core] persisted room (roomId={}, seq={}, messageId={})",
                event.roomId(), event.seq(), event.messageId());
    }
}

