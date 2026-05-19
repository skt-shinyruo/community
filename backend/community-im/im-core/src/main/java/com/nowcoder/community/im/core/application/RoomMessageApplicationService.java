package com.nowcoder.community.im.core.application;

import com.nowcoder.community.im.common.command.SendRoomTextCommand;
import com.nowcoder.community.im.common.event.ImEventIds;
import com.nowcoder.community.im.common.event.RoomMessageCommittedEvent;
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
        var result = roomMessageDomainService.persist(
                command.roomId(),
                command.fromUserId(),
                command.content(),
                command.clientMsgId()
        );
        var message = result.message();
        RoomMessagePersistedEvent event = new RoomMessagePersistedEvent(
                ImEventIds.roomMessageFact(message.roomId(), message.seq()),
                message.roomId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.createdAt().toEpochMilli()
        );
        if (result.created()) {
            outboxEnqueuer.enqueueRoomPersisted(event);
        }
        outboxEnqueuer.enqueueRoomCommitted(new RoomMessageCommittedEvent(
                ImEventIds.roomSendResult(command.requestId(), command.clientMsgId(), command.fromUserId()),
                command.requestId(),
                command.clientMsgId(),
                message.fromUserId(),
                message.roomId(),
                message.messageId(),
                message.seq(),
                message.createdAt().toEpochMilli()
        ));
        return event;
    }
}
