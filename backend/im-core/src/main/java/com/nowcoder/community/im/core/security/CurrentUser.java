package com.nowcoder.community.im.core.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static int userIdOrThrow(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("missing jwt");
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException("missing jwt.sub");
        }
        try {
            return Integer.parseInt(sub.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid jwt.sub: " + sub);
        }
    }
}

