package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.ConversationUnreadItem;

public record ConversationUnreadDataObject(
        String conversationId,
        long lastSeq,
        long lastReadSeq,
        long unreadCount
) {

    public ConversationUnreadItem toDomain() {
        return new ConversationUnreadItem(conversationId, lastSeq, lastReadSeq, unreadCount);
    }
}
