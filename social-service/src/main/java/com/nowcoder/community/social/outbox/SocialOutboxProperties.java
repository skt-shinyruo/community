package com.nowcoder.community.social.outbox;

// Outbox 配置：控制是否启用 outbox 以及投递批次与重试参数。
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "social.events.outbox")
public class SocialOutboxProperties {

    /**
     * 是否启用 Outbox（默认开启：可靠投递作为默认安全态）。
     */
    private boolean enabled = true;

    /**
     * Relay 是否运行（默认开启，需配合 enabled=true）。
     */
    private boolean relayEnabled = true;

    /**
     * 单次拉取事件数。
     */
    private int batchSize = 200;

    /**
     * 最大重试次数。
     */
    private int maxRetries = 10;

    /**
     * 基础退避（毫秒）。
     */
    private long baseDelayMs = 1000;

    /**
     * 最大退避（毫秒）。
     */
    private long maxDelayMs = 60000;

    /**
     * Relay 轮询间隔（毫秒）。
     */
    private long relayIntervalMs = 5000;

    /**
     * Kafka send 超时时间（毫秒）。
     */
    private long sendTimeoutMs = 3000;

    /**
     * SENDING lease 超时（毫秒）：relay 在 markSending 后崩溃可能导致事件长期卡在 SENDING；
     * 超过该阈值将自动回收为 RETRY（至少一次语义）。
     */
    private long sendingStaleMs = 60000;

    /**
     * 单次回收 SENDING 事件的最大数量。
     */
    private int recoverBatchSize = 200;

    /**
     * 是否启用 SENT 历史事件清理（默认关闭：保守，需运维评估后开启）。
     */
    private boolean cleanupEnabled = false;

    /**
     * SENT 事件保留天数（仅在 cleanupEnabled=true 生效）。
     */
    private int sentRetentionDays = 14;

    /**
     * 单次清理 SENT 事件的最大数量。
     */
    private int cleanupBatchSize = 500;

    /**
     * 清理任务轮询间隔（毫秒）。
     */
    private long cleanupIntervalMs = 21600000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public long getRelayIntervalMs() {
        return relayIntervalMs;
    }

    public void setRelayIntervalMs(long relayIntervalMs) {
        this.relayIntervalMs = relayIntervalMs;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public long getSendingStaleMs() {
        return sendingStaleMs;
    }

    public void setSendingStaleMs(long sendingStaleMs) {
        this.sendingStaleMs = sendingStaleMs;
    }

    public int getRecoverBatchSize() {
        return recoverBatchSize;
    }

    public void setRecoverBatchSize(int recoverBatchSize) {
        this.recoverBatchSize = recoverBatchSize;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public int getSentRetentionDays() {
        return sentRetentionDays;
    }

    public void setSentRetentionDays(int sentRetentionDays) {
        this.sentRetentionDays = sentRetentionDays;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}
