package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.domain.model.LoginRateLimitKey;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Service
public class LoginRateLimitDomainService {

    public LoginRateLimitKey keyOf(String username, String ip) {
        String normalizedUsername = StringUtils.hasText(username)
                ? username.trim().toLowerCase(Locale.ROOT)
                : "";
        String normalizedIp = StringUtils.hasText(ip) ? ip.trim() : "";
        return new LoginRateLimitKey(normalizedUsername, normalizedIp);
    }
}
