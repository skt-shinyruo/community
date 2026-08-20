package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;

import java.time.Instant;
import java.util.UUID;

public record RoomMessageDataObject(
        UUID roomId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        String content,
        String clientMsgId,
        Instant createdAt
) {

    public static RoomMessageDataObject fromDomain(RoomMessageRecord message) {
        return new RoomMessageDataObject(
                message.roomId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.content(),
                message.clientMsgId(),
                message.createdAt()
        );
    }

    public RoomMessageRecord toDomain() {
        return new RoomMessageRecord(roomId, seq, messageId, fromUserId, content, clientMsgId, createdAt);
    }
}
