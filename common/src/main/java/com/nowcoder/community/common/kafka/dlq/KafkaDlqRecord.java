package com.nowcoder.community.common.kafka.dlq;

import java.time.Instant;

/**
 * Kafka DLQ 记录（统一 schema，便于跨服务排障与回放）。
 *
 * <p>注意：payload 可能包含敏感信息，生产建议在发布前做裁剪/脱敏（本实现默认做长度裁剪）。</p>
 */
public class KafkaDlqRecord {

    private String traceId;
    private String originalTopic;
    private int originalPartition;
    private long originalOffset;
    private String key;
    private String payload;
    private String errorType;
    private String errorMessage;
    private Instant failedAt;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

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

