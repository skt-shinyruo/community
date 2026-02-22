package com.nowcoder.community.infra.security.origin;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.origin-guard")
public class OriginGuardProperties {

    private boolean enabled = true;

    private List<String> allowedOrigins = new ArrayList<>();

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
        this.allowedOrigins = allowedOrigins == null ? new ArrayList<>() : allowedOrigins;
    }

    public boolean isFailOpenWhenAllowlistEmpty() {
        return failOpenWhenAllowlistEmpty;
    }

    public void setFailOpenWhenAllowlistEmpty(boolean failOpenWhenAllowlistEmpty) {
        this.failOpenWhenAllowlistEmpty = failOpenWhenAllowlistEmpty;
    }
}
