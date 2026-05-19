package com.nowcoder.community.im.core.domain.model;

import java.time.Instant;
import java.util.UUID;

public record PrivateMessageRecord(
        String conversationId,
        long seq,
        UUID messageId,
        UUID fromUserId,
        UUID toUserId,
        String content,
        String clientMsgId,
        Instant createdAt
) {
}
