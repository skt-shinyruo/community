package com.nowcoder.community.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "events.outbox")
public class OutboxProperties {

    /**
     * Enables the outbox worker and BEFORE_COMMIT enqueuers.
     */
    private boolean enabled = false;

    /**
     * Max number of events processed per poll.
     */
    private int batchSize = 50;

    /**
     * Lease time for PROCESSING state. Used for crash recovery.
     */
    private Duration processingLease = Duration.ofSeconds(30);

    /**
     * Max retry attempts before the event is moved to DEAD.
     */
    private int maxRetries = 50;

    /**
     * Base backoff for retry scheduling.
     */
    private Duration baseBackoff = Duration.ofSeconds(5);

    /**
     * Max backoff cap.
     */
    private Duration maxBackoff = Duration.ofMinutes(10);

    /**
     * Worker fixed delay (millis).
     */
    private long workerFixedDelayMs = 1000L;

    /**
     * Max events moved by lease reaper per poll.
     */
    private int recoverLimit = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    public Duration getProcessingLease() {
        return processingLease;
    }

    public void setProcessingLease(Duration processingLease) {
        this.processingLease = processingLease == null ? Duration.ofSeconds(30) : processingLease;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, maxRetries);
    }

    public Duration getBaseBackoff() {
        return baseBackoff;
    }

    public void setBaseBackoff(Duration baseBackoff) {
        this.baseBackoff = baseBackoff == null ? Duration.ofSeconds(5) : baseBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff == null ? Duration.ofMinutes(10) : maxBackoff;
    }

    public long getWorkerFixedDelayMs() {
        return workerFixedDelayMs;
    }

    public void setWorkerFixedDelayMs(long workerFixedDelayMs) {
        this.workerFixedDelayMs = Math.max(100L, workerFixedDelayMs);
    }

    public int getRecoverLimit() {
        return recoverLimit;
    }

    public void setRecoverLimit(int recoverLimit) {
        this.recoverLimit = Math.max(0, recoverLimit);
    }
}
