package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;

import java.util.Locale;

public class LoginRateLimitDomainService {

    public LoginRateLimitKey keyOf(String username, String ip) {
        String normalizedUsername = hasText(username)
                ? username.trim().toLowerCase(Locale.ROOT)
                : "";
        String normalizedIp = hasText(ip) ? ip.trim() : "";
        return new LoginRateLimitKey(normalizedUsername, normalizedIp);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
