package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.captcha")
public class CaptchaProperties {

    /**
     * 存储方式：redis / memory（测试用）。
     */
    private String store = "redis";

    /**
     * 验证码有效期（秒），默认 60 秒。
     */
    private int ttlSeconds = 60;

    /**
     * 单个验证码允许失败次数；达到阈值后作废并要求重新获取。
     */
    private int maxFailures = 3;

    /**
     * 单个客户端 IP 在窗口内最多允许发起验证码获取次数，<= 0 表示关闭 IP 维度限流。
     */
    private int maxIssueRequestsPerIp = 10;

    /**
     * 测试用途：固定验证码内容（为空表示随机生成）。
     * 生产环境不建议开启。
     */
    private String fixedCode = "";

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

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public int getMaxIssueRequestsPerIp() {
        return maxIssueRequestsPerIp;
    }

    public void setMaxIssueRequestsPerIp(int maxIssueRequestsPerIp) {
        this.maxIssueRequestsPerIp = maxIssueRequestsPerIp;
    }

    public String getFixedCode() {
        return fixedCode;
    }

    public void setFixedCode(String fixedCode) {
        this.fixedCode = fixedCode;
    }
}
