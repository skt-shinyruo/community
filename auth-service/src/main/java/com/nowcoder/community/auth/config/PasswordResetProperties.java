package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-reset")
public class PasswordResetProperties {

    /**
     * 存储方式：redis / memory（测试用）。
     */
    private String store = "redis";

    /**
     * 重置 token 有效期（秒），默认 10 分钟。
     */
    private int ttlSeconds = 600;

    /**
     * 是否在响应中回传 resetLink（仅本地/测试联调建议开启）。
     */
    private boolean exposeResetLink = false;

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isExposeResetLink() {
        return exposeResetLink;
    }

    public void setExposeResetLink(boolean exposeResetLink) {
        this.exposeResetLink = exposeResetLink;
    }
}
