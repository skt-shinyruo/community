package com.nowcoder.community.im.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "im.cors")
public class ImCoreCorsProperties {

    private List<String> allowedOrigins = List.of();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
