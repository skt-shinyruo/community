package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.user.domain.model.UserRole;

import java.util.List;

public class UserCredentialDomainService {

    public String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isBcrypt(String stored) {
        return hasText(stored)
                && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    public List<String> authoritiesForType(int type) {
        return UserRole.requireValid(type).authorities();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
