package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;

import java.time.Instant;
import java.util.UUID;

public record PrivateMessageDataObject(
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        String clientMsgId,
        Instant createdAt
) {

    public static PrivateMessageDataObject fromDomain(PrivateMessageRecord message) {
        return new PrivateMessageDataObject(
                message.conversationId(),
                message.seq(),
                message.messageId(),
                message.fromUserId(),
                message.toUserId(),
                message.content(),
                message.clientMsgId(),
                message.createdAt()
        );
    }

    public PrivateMessageRecord toDomain() {
        return new PrivateMessageRecord(conversationId, seq, messageId, fromUserId, toUserId, content, clientMsgId, createdAt);
    }
}
