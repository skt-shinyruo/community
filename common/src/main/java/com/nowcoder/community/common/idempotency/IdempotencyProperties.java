package com.nowcoder.community.common.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * HTTP 幂等保护配置（SSOT：common）。
 */
@ConfigurationProperties(prefix = "http.idempotency")
public class IdempotencyProperties {

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
}

