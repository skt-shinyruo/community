package com.nowcoder.community.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    /**
     * HMAC 密钥（HS256）。建议通过环境变量或 Nacos Config 注入，避免提交到仓库。
     */
    private String hmacSecret;

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }
}

