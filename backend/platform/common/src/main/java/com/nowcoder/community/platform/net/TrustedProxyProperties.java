package com.nowcoder.community.platform.net;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务侧可信代理配置：用于决定是否信任 X-Forwarded-For 等转发头。
 *
 * <p>约定：仅当 remoteAddr ∈ CIDR 白名单时才解析 XFF，避免客户端伪造转发头绕过风控/限流。</p>
 */
@ConfigurationProperties(prefix = "gateway.trusted-proxy")
public class TrustedProxyProperties {

    /**
     * 是否启用可信代理模式。默认 false（不信任 XFF）。
     */
    private boolean enabled = false;

    /**
     * 可信代理 CIDR 列表（例如 10.0.0.0/8）。
     */
    private List<String> cidrs = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCidrs() {
        return cidrs;
    }

    public void setCidrs(List<String> cidrs) {
        this.cidrs = cidrs == null ? new ArrayList<>() : cidrs;
    }
}

