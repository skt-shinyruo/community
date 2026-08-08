package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;

public class LoginRateLimitDomainService {

    public LoginRateLimitKey keyOf(String subject, String ip) {
        String normalizedSubject = hasText(subject) ? subject.trim() : "";
        String normalizedIp = hasText(ip) ? ip.trim() : "";
        return new LoginRateLimitKey(normalizedSubject, normalizedIp);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
