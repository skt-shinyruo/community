package com.nowcoder.community.gateway.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("gateway.trusted-proxy")
public class EdgeTrustedProxyProperties {

    public static final String SOURCE_APPLICATION_DEFAULT = "application-default";
    public static final String SOURCE_COMPOSE_ENVIRONMENT = "compose-environment";

    private boolean enabled;
    private List<String> cidrs = new ArrayList<>();
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
