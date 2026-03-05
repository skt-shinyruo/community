package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * user-service -> social-service 同步调用配置。
 *
 * <p>说明：在 A-1 模块化单体形态下，该调用是进程内内部接口调用；保留 “client” 抽象是为了未来可替换为 RPC/HTTP。</p>
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
