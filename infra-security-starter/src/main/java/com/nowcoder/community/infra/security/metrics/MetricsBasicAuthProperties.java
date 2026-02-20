package com.nowcoder.community.infra.security.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "community.metrics.basic-auth")
public class MetricsBasicAuthProperties {

    /**
     * metrics basic-auth 用户名：用于访问 /actuator/prometheus。
     *
     * <p>说明：允许为空，缺省按 prometheus 处理。</p>
     */
    private String username;

    /**
     * metrics basic-auth 密码：用于访问 /actuator/prometheus。
     *
     * <p>安全约束：必须显式配置（fail-closed）。</p>
     */
    private String password;

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
