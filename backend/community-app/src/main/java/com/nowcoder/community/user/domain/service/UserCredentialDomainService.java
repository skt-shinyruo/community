package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.user.domain.model.UserAccount;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public class UserCredentialDomainService {

    public String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isBcrypt(String stored) {
        return hasText(stored)
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public boolean isLegacyPassword(UserAccount user) {
        return user != null && hasText(user.encodedPassword()) && !isBcrypt(user.encodedPassword());
    }

    public boolean legacyPasswordMatches(UserAccount user, String rawPassword) {
        if (user == null || !hasText(rawPassword) || !hasText(user.salt())) {
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String md5(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is not available", e);
        }
    }
}
