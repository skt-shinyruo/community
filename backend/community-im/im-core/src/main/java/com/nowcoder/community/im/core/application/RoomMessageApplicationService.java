package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.RoomMessagePersistedEvent;
import com.nowcoder.community.im.core.domain.service.RoomMessageDomainService;
import com.nowcoder.community.im.core.outbox.ImMessageOutboxEnqueuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomMessageApplicationService {

    private final RoomMessageDomainService roomMessageDomainService;
    private final ImMessageOutboxEnqueuer outboxEnqueuer;

    public RoomMessageApplicationService(
            RoomMessageDomainService roomMessageDomainService,
            ImMessageOutboxEnqueuer outboxEnqueuer
    ) {
        this.roomMessageDomainService = roomMessageDomainService;
        this.outboxEnqueuer = outboxEnqueuer;
    }

    @Transactional
    public RoomMessagePersistedEvent persist(SendRoomTextCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("command required");
        }
        var message = roomMessageDomainService.persist(
                command.roomId(),
                command.fromUserId(),
                command.content(),
                command.clientMsgId()
        );
        RoomMessagePersistedEvent event = new RoomMessagePersistedEvent(
                "evt_" + message.messageId(),
                message.roomId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                command.requestId(),
                command.clientMsgId(),
                message.createdAt().toEpochMilli()
        );
        outboxEnqueuer.enqueueRoomPersisted(event);
        return event;
    }
}
