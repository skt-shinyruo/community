package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关请求体大小限制配置（R24）：
 * - 主要用于防 DoS 与脏数据污染（异常 payload 直接在网关拒绝，不进入下游）
 * - 默认只对写方法（POST/PUT/PATCH）生效
 */
@ConfigurationProperties(prefix = "gateway.request-size-limit")
public class RequestSizeLimitProperties {

    private boolean enabled = true;

    /**
     * 最大允许的 Content-Length（字节）。默认 1 MiB。
     */
    private long maxBytes = 1024 * 1024;

    /**
     * 当 Content-Length 缺失（chunked/unknown）时的行为：
     * - true：fail-closed（直接拒绝）
     * - false：放行（兼容性更好，但无法在网关侧精确拦截）
     */
    private boolean failClosedWhenUnknown = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public boolean isFailClosedWhenUnknown() {
        return failClosedWhenUnknown;
    }

    public void setFailClosedWhenUnknown(boolean failClosedWhenUnknown) {
        this.failClosedWhenUnknown = failClosedWhenUnknown;
    }
}

