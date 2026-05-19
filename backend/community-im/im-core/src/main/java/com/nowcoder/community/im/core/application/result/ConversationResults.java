package com.nowcoder.community.im.core.application.result;

import java.util.List;
import java.util.UUID;

public final class ConversationResults {

    private ConversationResults() {
    }

    public record Messages(
            String conversationId,
            List<MessageItem> items,
            long nextAfterSeq,
            long lastReadSeq
    ) {
    }

    public record ListItem(
            String conversationId,
            UUID otherUserId,
            long lastSeq,
            long lastReadSeq,
            long unreadCount,
            LastMessage lastMessage
    ) {
    }

    public record LastMessage(
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            long createdAtEpochMs
    ) {
    }

    public record MessageItem(
            String conversationId,
            long seq,
            UUID messageId,
            UUID fromUserId,
            UUID toUserId,
            String content,
            String clientMsgId,
            long createdAtEpochMs
    ) {
    }
}
