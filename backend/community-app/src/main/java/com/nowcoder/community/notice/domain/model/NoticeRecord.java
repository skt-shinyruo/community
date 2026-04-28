package com.nowcoder.community.notice.domain.model;

import java.util.Date;
import java.util.UUID;

public class NoticeRecord {

    public static final UUID SYSTEM_NOTICE_SENDER_ID = new UUID(0L, 0L);

    private UUID id;
    private UUID senderUserId;
    private UUID recipientUserId;
    private String topic;
    private String content;
    private int status;
    private Date createTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public void setSenderUserId(UUID senderUserId) {
        this.senderUserId = senderUserId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(UUID recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
