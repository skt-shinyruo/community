package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * user-service -> social-service 同步调用配置。
 *
 * <p>生产 P0 目标：强制超时，避免下游抖动导致线程长期阻塞与级联雪崩。</p>
 */
@Component
@ConfigurationProperties(prefix = "user.social-client")
public class SocialServiceClientProperties {

    /**
     * social-service baseUrl（走服务发现时可用 http://social-service）。
     */
    private String baseUrl = "http://social-service";

    /**
     * 建连超时（默认 200ms）。
     */
    private Duration connectTimeout = Duration.ofMillis(200);

    /**
     * 读取超时（默认 800ms）。
     */
    private Duration readTimeout = Duration.ofMillis(800);

    /**
     * social-service internal API 访问令牌（X-Internal-Token）。
     *
     * <p>说明：该 token 属于“目标服务（social-service）”，用于调用 /internal/social/**。</p>
     */
    private String internalToken = "";

    /**
     * 是否允许降级（fail-open）。只推荐用于非关键读路径（例如计数类展示）。
     */
    private boolean failOpen = true;

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

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
