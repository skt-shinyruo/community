package com.nowcoder.community.im.core.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ConversationListItem(
        String conversationId,
        UUID otherUserId,
        long lastSeq,
        long lastReadSeq,
        long unreadCount,
        LastMessage lastMessage,
        Instant sortAt
) {

    public record LastMessage(
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            Instant createdAt
    ) {
    }
}
