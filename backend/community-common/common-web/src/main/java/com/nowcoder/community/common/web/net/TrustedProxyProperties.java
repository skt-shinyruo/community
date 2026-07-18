package com.nowcoder.community.common.web.net;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务侧可信代理配置：用于决定是否信任 X-Forwarded-For 等转发头。
 *
 * <p>约定：仅当 remoteAddr ∈ CIDR 白名单时才解析 XFF，避免客户端伪造转发头绕过风控/限流。</p>
 */
@ConfigurationProperties(prefix = "community.web.trusted-proxy")
public class TrustedProxyProperties {

    public static final String SOURCE_APPLICATION_DEFAULT = "application-default";
    public static final String SOURCE_COMPOSE_ENVIRONMENT = "compose-environment";

    /**
     * 是否启用可信代理模式。默认 false（不信任 XFF）。
     */
    private boolean enabled = false;

    /**
     * 可信代理 CIDR 列表（例如 10.0.0.0/8）。
     */
    private List<String> cidrs = new ArrayList<>();

    /**
     * Low-cardinality label describing where this configuration was supplied.
     */
    private String source = SOURCE_APPLICATION_DEFAULT;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = SOURCE_COMPOSE_ENVIRONMENT.equals(source)
                ? SOURCE_COMPOSE_ENVIRONMENT
                : SOURCE_APPLICATION_DEFAULT;
    }
}
