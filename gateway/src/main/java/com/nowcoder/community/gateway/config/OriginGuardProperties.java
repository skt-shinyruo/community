package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.origin-guard")
public class OriginGuardProperties {

    /**
     * 是否启用 Origin 白名单校验。
     *
     * 说明：该校验用于 cookie 会话相关敏感接口（login/refresh/logout）的 CSRF 风险降低。
     */
    private boolean enabled = true;

    /**
     * 允许的 Origin 列表（精确匹配）。
     *
     * 为空时默认放行（与旧 auth-service 行为保持一致，避免误配置导致全站不可用）。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}

