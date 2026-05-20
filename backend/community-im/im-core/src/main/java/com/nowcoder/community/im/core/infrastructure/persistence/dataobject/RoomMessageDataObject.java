package com.nowcoder.community.im.core.infrastructure.persistence.dataobject;

import com.nowcoder.community.im.core.domain.model.RoomMessageRecord;

import java.time.Instant;
import java.util.UUID;

public class RoomMessageDataObject {

    private UUID roomId;
    private long seq;
    private UUID messageId;
    private UUID fromUserId;
    private String content;
    private String clientMsgId;
    private Instant createdAt;

    public static RoomMessageDataObject fromDomain(RoomMessageRecord message) {
        RoomMessageDataObject row = new RoomMessageDataObject();
        row.setRoomId(message.roomId());
        row.setSeq(message.seq());
        row.setMessageId(message.messageId());
        row.setFromUserId(message.fromUserId());
        row.setContent(message.content());
        row.setClientMsgId(message.clientMsgId());
        row.setCreatedAt(message.createdAt());
        return row;
    }

    public RoomMessageRecord toDomain() {
        return new RoomMessageRecord(roomId, seq, messageId, fromUserId, content, clientMsgId, createdAt);
    }

    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
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
