package com.nowcoder.community.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * auth-service -> user-service 内部调用配置（用于登录/刷新/注册/激活/找回密码）。
 */
@Component
@ConfigurationProperties(prefix = "auth.user-client")
public class UserServiceClientProperties {

    private String baseUrl = "http://user-service";

    private Duration connectTimeout = Duration.ofMillis(200);

    private Duration readTimeout = Duration.ofMillis(800);

    /**
     * user-service internal API 访问令牌（X-Internal-Token）。
     *
     * <p>默认推荐使用统一 env：INTERNAL_TOKEN（同时 user-service 侧配置 user.internal-token）。</p>
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

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(internalToken)) {
            throw new IllegalStateException("auth.user-client.internal-token 未配置（建议设置 env: INTERNAL_TOKEN 或 USER_INTERNAL_TOKEN）");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("auth.user-client.base-url 未配置");
        }
    }
}

