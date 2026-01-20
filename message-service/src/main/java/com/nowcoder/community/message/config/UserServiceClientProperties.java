package com.nowcoder.community.message.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * message-service -> user-service 同步调用配置。
 *
 * <p>多实例上线场景下必须具备确定性超时，避免下游抖动导致线程长期阻塞与级联雪崩。</p>
 */
@Component
@ConfigurationProperties(prefix = "message.user-client")
public class UserServiceClientProperties {

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

