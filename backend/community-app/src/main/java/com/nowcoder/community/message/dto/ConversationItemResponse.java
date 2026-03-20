package com.nowcoder.community.message.dto;

public class ConversationItemResponse {

    private String conversationId;
    private LetterItemResponse lastMessage;
    private int letterCount;
    private int unreadCount;
    private UserSummaryResponse targetUser;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public LetterItemResponse getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(LetterItemResponse lastMessage) {
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
