package com.nowcoder.community.user.domain.service;

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
}
