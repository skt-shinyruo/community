package com.nowcoder.community.contracts.internal.dto;

public class OutboxHealthResponse {

    private int newCount;
    private int retryCount;
    private int sendingCount;
    private int failedCount;

    public int getNewCount() {
        return newCount;
    }

    public void setNewCount(int newCount) {
        this.newCount = newCount;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getSendingCount() {
        return sendingCount;
    }

    public void setSendingCount(int sendingCount) {
        this.sendingCount = sendingCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}

