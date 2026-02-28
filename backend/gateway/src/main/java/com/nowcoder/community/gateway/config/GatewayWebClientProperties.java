package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 WebClient 出站调用的统一兜底配置：
 * - 连接/响应/读写超时（避免请求悬挂）
 * - 连接池上限与 pending acquire 限制（避免资源堆积拖垮网关）
 *
 * <p>说明：业务调用点仍可按需设置更严格的局部 timeout（例如可丢弃的 analytics 采集链路），
 * 但全局兜底用于覆盖“新增链路忘配”与极端网络条件。</p>
 */
@ConfigurationProperties(prefix = "gateway.webclient")
public class GatewayWebClientProperties {

    /**
     * TCP 连接建立超时（毫秒）。
     */
    private int connectTimeoutMs = 1_000;

    /**
     * 请求响应超时（毫秒）。从发出请求到收到响应（含头部）超过该时间则失败。
     */
    private int responseTimeoutMs = 2_000;

    /**
     * Socket 读超时（毫秒）。用于处理“连接已建立但对端长时间不发送数据”的场景。
     */
    private int readTimeoutMs = 2_000;

    /**
     * Socket 写超时（毫秒）。用于处理“写入阻塞/对端不读导致回压”场景。
     */
    private int writeTimeoutMs = 2_000;

    private Pool pool = new Pool();

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public void setResponseTimeoutMs(int responseTimeoutMs) {
        this.responseTimeoutMs = responseTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getWriteTimeoutMs() {
        return writeTimeoutMs;
    }

    public void setWriteTimeoutMs(int writeTimeoutMs) {
        this.writeTimeoutMs = writeTimeoutMs;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool == null ? new Pool() : pool;
    }

    public static class Pool {

        /**
         * 连接池最大连接数。
         */
        private int maxConnections = 500;

        /**
         * 等待获取连接的超时（毫秒）。超过后会失败（保护网关不被排队拖垮）。
         */
        private int pendingAcquireTimeoutMs = 2_000;

        /**
         * 等待获取连接的最大排队数（-1 表示不限制，生产不建议）。
         */
        private int pendingAcquireMaxCount = 2_000;

        /**
         * 最大空闲时间（秒）。避免大量 idle 连接长期占用资源。
         */
        private int maxIdleTimeSeconds = 60;

        /**
         * 最大存活时间（秒）。用于避免长连接在网络设备/服务端半开后长期不可用。
         */
        private int maxLifeTimeSeconds = 300;

        /**
         * 后台清理周期（秒）。<=0 表示不启用后台清理线程。
         */
        private int evictInBackgroundSeconds = 60;

        /**
         * 是否启用连接池指标（Reactor Netty metrics）。
         */
        private boolean metricsEnabled = true;

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getPendingAcquireTimeoutMs() {
            return pendingAcquireTimeoutMs;
        }

        public void setPendingAcquireTimeoutMs(int pendingAcquireTimeoutMs) {
            this.pendingAcquireTimeoutMs = pendingAcquireTimeoutMs;
        }

        public int getPendingAcquireMaxCount() {
            return pendingAcquireMaxCount;
        }

        public void setPendingAcquireMaxCount(int pendingAcquireMaxCount) {
            this.pendingAcquireMaxCount = pendingAcquireMaxCount;
        }

        public int getMaxIdleTimeSeconds() {
            return maxIdleTimeSeconds;
        }

        public void setMaxIdleTimeSeconds(int maxIdleTimeSeconds) {
            this.maxIdleTimeSeconds = maxIdleTimeSeconds;
        }

        public int getMaxLifeTimeSeconds() {
            return maxLifeTimeSeconds;
        }

        public void setMaxLifeTimeSeconds(int maxLifeTimeSeconds) {
            this.maxLifeTimeSeconds = maxLifeTimeSeconds;
        }

        public int getEvictInBackgroundSeconds() {
            return evictInBackgroundSeconds;
        }

        public void setEvictInBackgroundSeconds(int evictInBackgroundSeconds) {
            this.evictInBackgroundSeconds = evictInBackgroundSeconds;
        }

        public boolean isMetricsEnabled() {
            return metricsEnabled;
        }

        public void setMetricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
        }
    }
}

