package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics.collect")
public class AnalyticsCollectProperties {

    private boolean enabled = false;

    /**
     * 采集请求超时（毫秒）。默认较小，避免影响主请求链路。
     */
    private int timeoutMs = 300;

    /**
     * 采集并发上限（worker in-flight）。
     *
     * <p>说明：当并发达到上限时，采集事件会在网关侧队列中排队；队列满则丢弃。</p>
     */
    private int maxConcurrency = 50;

    /**
     * 网关侧采集队列容量（有界）。
     *
     * <p>说明：采集链路必须与业务转发隔离，因此当下游不可用/积压时允许丢弃采集事件，
     * 通过指标观测并在必要时调小/关闭。</p>
     */
    private int queueCapacity = 10_000;

    /**
     * 是否启用网关侧 in-process 去重（有界 TTL 缓存）。默认开启，用于降噪与降低下游压力。
     */
    private boolean dedupEnabled = true;

    /**
     * UV 去重缓存最大 key 数（仅网关单实例内）。超过后由缓存淘汰，不应造成内存无界增长。
     */
    private long uvCacheMaxSize = 200_000;

    /**
     * DAU 去重缓存最大 key 数（仅网关单实例内）。
     */
    private long dauCacheMaxSize = 200_000;

    /**
     * 去重缓存 TTL（秒）。建议与“日”口径一致（默认 86400s）。
     */
    private long dedupTtlSeconds = 86_400;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public boolean isDedupEnabled() {
        return dedupEnabled;
    }

    public void setDedupEnabled(boolean dedupEnabled) {
        this.dedupEnabled = dedupEnabled;
    }

    public long getUvCacheMaxSize() {
        return uvCacheMaxSize;
    }

    public void setUvCacheMaxSize(long uvCacheMaxSize) {
        this.uvCacheMaxSize = uvCacheMaxSize;
    }

    public long getDauCacheMaxSize() {
        return dauCacheMaxSize;
    }

    public void setDauCacheMaxSize(long dauCacheMaxSize) {
        this.dauCacheMaxSize = dauCacheMaxSize;
    }

    public long getDedupTtlSeconds() {
        return dedupTtlSeconds;
    }

    public void setDedupTtlSeconds(long dedupTtlSeconds) {
        this.dedupTtlSeconds = dedupTtlSeconds;
    }
}
