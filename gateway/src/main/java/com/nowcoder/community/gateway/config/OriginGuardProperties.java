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
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * allowed-origins 为空时的行为：
     * - true：fail-open（兼容开发/演练环境，避免误配置导致全站不可用）
     * - false：fail-closed（建议生产启用，至少覆盖 login/refresh/logout）
     */
    private boolean failOpenWhenAllowlistEmpty = true;

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

    public boolean isFailOpenWhenAllowlistEmpty() {
        return failOpenWhenAllowlistEmpty;
    }

    public void setFailOpenWhenAllowlistEmpty(boolean failOpenWhenAllowlistEmpty) {
        this.failOpenWhenAllowlistEmpty = failOpenWhenAllowlistEmpty;
    }
}

