package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.ConversationListItem;

import java.time.Instant;
import java.util.UUID;

public record ConversationInboxDataObject(
        String conversationId,
        UUID peerUserId,
        long lastSeq,
        long lastReadSeq,
        long unreadCount,
        UUID lastMessageId,
        UUID lastFromUserId,
        UUID lastToUserId,
        String lastContent,
        Instant lastMessageCreatedAt,
        Instant sortAt
) {

    public ConversationListItem toListItem() {
        ConversationListItem.LastMessage lastMessage = lastMessageId == null ? null : new ConversationListItem.LastMessage(
                lastMessageId,
                lastFromUserId,
                lastToUserId,
                lastContent,
                lastMessageCreatedAt
        );
        return new ConversationListItem(
                conversationId,
                peerUserId,
                lastSeq,
                lastReadSeq,
                unreadCount,
                lastMessage,
                sortAt
        );
    }
}
