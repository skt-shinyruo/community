package com.nowcoder.community.content.outbox;

// Outbox 配置：控制是否启用 outbox 以及投递批次与重试参数。
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "content.events.outbox")
public class ContentOutboxProperties {

    /**
     * 是否启用 Outbox（默认关闭，可灰度开启）。
     */
    private boolean enabled = false;

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
}

