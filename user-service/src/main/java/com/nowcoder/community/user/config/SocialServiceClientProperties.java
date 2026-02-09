package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * user-service -> social-service 同步调用配置。
 *
 * <p>说明：服务间同步调用已迁移为 Dubbo RPC，因此此处仅保留“是否允许降级（fail-open）”开关。</p>
 */
@Component
@ConfigurationProperties(prefix = "user.social-client")
public class SocialServiceClientProperties {

    /**
     * 是否允许降级（fail-open）。只推荐用于非关键读路径（例如计数类展示）。
     */
    private boolean failOpen = true;

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
