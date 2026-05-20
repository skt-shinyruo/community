package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import java.time.Instant;
import java.util.UUID;

public class RoomLastMessageDataObject {

    private UUID messageId;
    private UUID fromUserId;
    private String content;
    private Instant createdAt;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
