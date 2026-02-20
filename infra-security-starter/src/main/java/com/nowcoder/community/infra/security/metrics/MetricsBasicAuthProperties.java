package com.nowcoder.community.infra.security.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "community.metrics.basic-auth")
public class MetricsBasicAuthProperties {

    private String username = "prometheus";
    private String password = "dev-prometheus-pass";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

