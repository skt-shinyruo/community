package com.nowcoder.community.content.kafka;

import java.time.Instant;

public class KafkaDlqRecord {

    private String originalTopic;
    private int originalPartition;
    private long originalOffset;
    private String key;
    private String payload;
    private String errorType;
    private String errorMessage;
    private Instant failedAt;

    public String getOriginalTopic() {
        return originalTopic;
    }

    public void setOriginalTopic(String originalTopic) {
        this.originalTopic = originalTopic;
    }

    public int getOriginalPartition() {
        return originalPartition;
    }

    public void setOriginalPartition(int originalPartition) {
        this.originalPartition = originalPartition;
    }

    public long getOriginalOffset() {
        return originalOffset;
    }

    public void setOriginalOffset(long originalOffset) {
        this.originalOffset = originalOffset;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }
}

