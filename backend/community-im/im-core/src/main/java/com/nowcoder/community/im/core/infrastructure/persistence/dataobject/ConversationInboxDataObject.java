package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.ConversationListItem;

import java.time.Instant;
import java.util.UUID;

public class ConversationInboxDataObject {

    private String conversationId;
    private UUID peerUserId;
    private long lastSeq;
    private long lastReadSeq;
    private long unreadCount;
    private UUID lastMessageId;
    private UUID lastFromUserId;
    private UUID lastToUserId;
    private String lastContent;
    private Instant lastMessageCreatedAt;

    public ConversationListItem toListItem() {
        ConversationListItem.LastMessage lastMessage = lastMessageId == null ? null : new ConversationListItem.LastMessage(
                lastMessageId,
                lastFromUserId,
                lastToUserId,
                lastContent,
                lastMessageCreatedAt
        );
        return new ConversationListItem(conversationId, peerUserId, lastSeq, lastReadSeq, unreadCount, lastMessage);
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public UUID getPeerUserId() {
        return peerUserId;
    }

    public void setPeerUserId(UUID peerUserId) {
        this.peerUserId = peerUserId;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public void setLastSeq(long lastSeq) {
        this.lastSeq = lastSeq;
    }

    public long getLastReadSeq() {
        return lastReadSeq;
    }

    public void setLastReadSeq(long lastReadSeq) {
        this.lastReadSeq = lastReadSeq;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    public UUID getLastMessageId() {
        return lastMessageId;
    }

    public void setLastMessageId(UUID lastMessageId) {
        this.lastMessageId = lastMessageId;
    }

    public UUID getLastFromUserId() {
        return lastFromUserId;
    }

    public void setLastFromUserId(UUID lastFromUserId) {
        this.lastFromUserId = lastFromUserId;
    }

    public UUID getLastToUserId() {
        return lastToUserId;
    }

    public void setLastToUserId(UUID lastToUserId) {
        this.lastToUserId = lastToUserId;
    }

    public String getLastContent() {
        return lastContent;
    }

    public void setLastContent(String lastContent) {
        this.lastContent = lastContent;
    }

    public Instant getLastMessageCreatedAt() {
        return lastMessageCreatedAt;
    }

    public void setLastMessageCreatedAt(Instant lastMessageCreatedAt) {
        this.lastMessageCreatedAt = lastMessageCreatedAt;
    }
}
