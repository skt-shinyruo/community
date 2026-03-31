package com.nowcoder.community.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * HTTP 幂等保护配置（SSOT：infra-idempotency-starter）。
 */
@ConfigurationProperties(prefix = "http.idempotency")
public class IdempotencyProperties {

    /**
     * 是否启用 HTTP 幂等保护：
     * - 默认关闭，避免在未使用幂等保护的服务中引入不必要的依赖与启动失败风险
     * - 对需要幂等的服务（如发帖/评论/私信）应显式打开
     */
    private boolean enabled = false;

    /**
     * 幂等存储后端：
     * - REDIS：默认（兼容旧实现）
     * - DB：MySQL（用于消除 Redis 抖动放大，作为更强的 SSOT）
     */
    private Store store = Store.REDIS;

    /**
     * processing 状态的 TTL：用于并发互斥与“处理中”提示。
     *
     * <p>注意：该值过短可能导致慢链路下锁过期 → 二次执行的理论风险；可按部署环境调整。</p>
     */
    private Duration processingTtl = Duration.ofSeconds(30);

    /**
     * success 状态的 TTL：用于重复请求直接复用响应。
     */
    private Duration successTtl = Duration.ofHours(24);

    public Duration getProcessingTtl() {
        return processingTtl;
    }

    public void setProcessingTtl(Duration processingTtl) {
        this.processingTtl = processingTtl;
    }

    public Duration getSuccessTtl() {
        return successTtl;
    }

    public void setSuccessTtl(Duration successTtl) {
        this.successTtl = successTtl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store == null ? Store.REDIS : store;
    }

    public enum Store {
        REDIS,
        DB
    }
}
