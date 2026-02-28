package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.audit")
public class GatewayAuditProperties {

    /**
     * 是否启用网关审计日志。
     *
     * <p>说明：审计属于可观测性能力，应尽量保持“低成本、可关闭、不中断主链路”。</p>
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

