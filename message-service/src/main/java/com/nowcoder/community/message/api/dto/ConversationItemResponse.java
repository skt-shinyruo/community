package com.nowcoder.community.message.api.dto;

import com.nowcoder.community.message.entity.Message;

public class ConversationItemResponse {

    private String conversationId;
    private Message lastMessage;
    private int letterCount;
    private int unreadCount;
    private UserSummaryResponse targetUser;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Message getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(Message lastMessage) {
        this.lastMessage = lastMessage;
    }

    public int getLetterCount() {
        return letterCount;
    }

    public void setLetterCount(int letterCount) {
        this.letterCount = letterCount;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public UserSummaryResponse getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(UserSummaryResponse targetUser) {
        this.targetUser = targetUser;
    }
}

