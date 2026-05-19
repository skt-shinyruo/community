package com.nowcoder.community.im.core.application.result;

import java.util.List;
import java.util.UUID;

public record UnreadSummaryResult(
        List<RoomUnreadItem> rooms,
        List<ConversationUnreadItem> conversations
) {

    public record RoomUnreadItem(UUID roomId, long lastSeq, long lastReadSeq, long unreadCount) {
    }

    public record ConversationUnreadItem(String conversationId, long lastSeq, long lastReadSeq, long unreadCount) {
    }
}
