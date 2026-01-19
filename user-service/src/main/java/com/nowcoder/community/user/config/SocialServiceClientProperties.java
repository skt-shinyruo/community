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
     * 建连超时（默认 200ms）。
     */
    private Duration connectTimeout = Duration.ofMillis(200);

    /**
     * 读取超时（默认 800ms）。
     */
    private Duration readTimeout = Duration.ofMillis(800);

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
}

