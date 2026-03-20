package com.nowcoder.community.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.login-rate-limit")
public class LoginRateLimitProperties {

    private boolean enabled = true;
    private int windowSeconds = 60;
    private int maxFailuresPerIp = 20;
    private int maxFailuresPerUser = 5;

    /**
     * 登录失败达到该阈值后，要求验证码（风险触发）。
     */
    private int captchaRequiredFailuresPerIp = 5;

    /**
     * 登录失败达到该阈值后，要求验证码（风险触发）。
     */
    private int captchaRequiredFailuresPerUser = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxFailuresPerIp() {
        return maxFailuresPerIp;
    }

    public void setMaxFailuresPerIp(int maxFailuresPerIp) {
        this.maxFailuresPerIp = maxFailuresPerIp;
    }

    public int getMaxFailuresPerUser() {
        return maxFailuresPerUser;
    }

    public void setMaxFailuresPerUser(int maxFailuresPerUser) {
        this.maxFailuresPerUser = maxFailuresPerUser;
    }

    public int getCaptchaRequiredFailuresPerIp() {
        return captchaRequiredFailuresPerIp;
    }

    public void setCaptchaRequiredFailuresPerIp(int captchaRequiredFailuresPerIp) {
        this.captchaRequiredFailuresPerIp = captchaRequiredFailuresPerIp;
    }

    public int getCaptchaRequiredFailuresPerUser() {
        return captchaRequiredFailuresPerUser;
    }

    public void setCaptchaRequiredFailuresPerUser(int captchaRequiredFailuresPerUser) {
        this.captchaRequiredFailuresPerUser = captchaRequiredFailuresPerUser;
    }
}
