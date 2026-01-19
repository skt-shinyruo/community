package com.nowcoder.community.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * search-service -> content-service 内部调用配置（用于重建索引等后台任务）。
 */
@Component
@ConfigurationProperties(prefix = "search.content-client")
public class ContentServiceClientProperties {

    /**
     * content-service 的内部访问地址（Docker Compose 下默认走服务名 + 固定端口）。
     *
     * <p>如果运行环境具备服务发现并能自动解析端口，可配置为 http://content-service 。</p>
     */
    private String baseUrl = "http://content-service:8088";

    /**
     * 建连超时（默认 200ms）。
     */
    private Duration connectTimeout = Duration.ofMillis(200);

    /**
     * 读取超时（默认 5s）。重建索引属于后台任务，可能返回较大 payload，因此默认值比在线链路更宽松。
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    /**
     * 每次扫描拉取数量（默认 500，最大建议不超过 1000）。
     */
    private int pageSize = 500;

    /**
     * content-service 内部接口访问令牌（X-Internal-Token）。
     */
    private String internalToken = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }
}
