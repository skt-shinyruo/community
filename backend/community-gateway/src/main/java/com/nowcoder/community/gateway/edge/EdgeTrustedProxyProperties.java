package com.nowcoder.community.gateway.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("gateway.trusted-proxy")
public class EdgeTrustedProxyProperties {

    private boolean enabled;
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
