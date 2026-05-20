package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.PrivateMessageRecord;

import java.time.Instant;
import java.util.UUID;

public class PrivateMessageDataObject {

    private String conversationId;
    private long seq;
    private UUID messageId;
    private UUID fromUserId;
    private UUID toUserId;
    private String content;
    private String clientMsgId;
    private Instant createdAt;

    public static PrivateMessageDataObject fromDomain(PrivateMessageRecord message) {
        PrivateMessageDataObject row = new PrivateMessageDataObject();
        row.setConversationId(message.conversationId());
        row.setSeq(message.seq());
        row.setMessageId(message.messageId());
        row.setFromUserId(message.fromUserId());
        row.setToUserId(message.toUserId());
        row.setContent(message.content());
        row.setClientMsgId(message.clientMsgId());
        row.setCreatedAt(message.createdAt());
        return row;
    }

    public PrivateMessageRecord toDomain() {
        return new PrivateMessageRecord(conversationId, seq, messageId, fromUserId, toUserId, content, clientMsgId, createdAt);
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public long getSeq() {
        return seq;
    }

    public void setSeq(long seq) {
        this.seq = seq;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(UUID fromUserId) {
        this.fromUserId = fromUserId;
    }

    public UUID getToUserId() {
        return toUserId;
    }

    public void setToUserId(UUID toUserId) {
        this.toUserId = toUserId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
