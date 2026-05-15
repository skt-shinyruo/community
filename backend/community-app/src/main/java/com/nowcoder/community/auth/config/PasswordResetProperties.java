package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.password-reset")
public class PasswordResetProperties {

    /**
     * 重置密码链接的 base URL（建议指向对用户可访问的入口，例如 gateway/前端域名）。
     *
     * <p>说明：为了避免在非本地环境“静默回退到 localhost”生成错误链接，这里不提供硬编码默认值；
     * 未配置时将拒绝签发 resetLink。</p>
     */
    private String resetBaseUrl = "";

    /**
     * 存储方式：redis / memory（测试用）。
     */
    private String store = "redis";

    /**
     * 重置 token 有效期（秒），默认 10 分钟。
     */
    private int ttlSeconds = 600;

    /**
     * 重置密码请求限流窗口（秒）。
     */
    private int requestWindowSeconds = 3600;

    /**
     * 单个邮箱在窗口内最多允许请求次数，<= 0 表示关闭邮箱维度限流。
     */
    private int maxRequestsPerEmail = 3;

    /**
     * 单个客户端 IP 在窗口内最多允许请求次数，<= 0 表示关闭 IP 维度限流。
     */
    private int maxRequestsPerIp = 20;

    public String getResetBaseUrl() {
        return resetBaseUrl;
    }

    public void setResetBaseUrl(String resetBaseUrl) {
        this.resetBaseUrl = resetBaseUrl;
    }

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

    public int getRequestWindowSeconds() {
        return requestWindowSeconds;
    }

    public void setRequestWindowSeconds(int requestWindowSeconds) {
        this.requestWindowSeconds = requestWindowSeconds;
    }

    public int getMaxRequestsPerEmail() {
        return maxRequestsPerEmail;
    }

    public void setMaxRequestsPerEmail(int maxRequestsPerEmail) {
        this.maxRequestsPerEmail = maxRequestsPerEmail;
    }

    public int getMaxRequestsPerIp() {
        return maxRequestsPerIp;
    }

    public void setMaxRequestsPerIp(int maxRequestsPerIp) {
        this.maxRequestsPerIp = maxRequestsPerIp;
    }
}
