package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.user.domain.model.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class UserCredentialDomainService {

    public String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isBcrypt(String stored) {
        return StringUtils.hasText(stored)
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public boolean isLegacyPassword(UserAccount user) {
        return user != null && StringUtils.hasText(user.encodedPassword()) && !isBcrypt(user.encodedPassword());
    }

    public boolean legacyPasswordMatches(UserAccount user, String rawPassword) {
        if (user == null || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(user.salt())) {
            return false;
        }
        return user.encodedPassword().equals(md5(rawPassword + user.salt()));
    }

    public List<String> authoritiesForType(int type) {
        if (type == 1) {
            return List.of("ROLE_ADMIN");
        }
        if (type == 2) {
            return List.of("ROLE_MODERATOR");
        }
        return List.of("ROLE_USER");
    }

    private String md5(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8));
    }
}
