package com.nowcoder.community.auth.domain.model;

public record LoginRateLimitKey(String subject, String ip) {

    public LoginRateLimitKey {
        subject = normalize(subject);
        ip = normalize(ip);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
