package com.nowcoder.community.im.core.domain.model;

public record ConversationUnreadItem(String conversationId, long lastSeq, long lastReadSeq, long unreadCount) {
}
