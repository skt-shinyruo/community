package com.nowcoder.community.common.security.jwt;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtSubjects {

    private JwtSubjects() {
    }

    public static int userIdOrThrow(Jwt jwt) {
        Integer userId = tryUserId(jwt);
        if (userId == null || userId <= 0) {
            String sub = jwt == null ? null : jwt.getSubject();
            throw new IllegalArgumentException("invalid jwt.sub: " + sub);
        }
        return userId;
    }

    public static Integer tryUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(sub.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static UUID userUuidOrThrow(Jwt jwt) {
        UUID userId = tryUserUuid(jwt);
        if (userId == null) {
            String sub = jwt == null ? null : jwt.getSubject();
            throw new IllegalArgumentException("invalid jwt.sub: " + sub);
        }
        return userId;
    }

    public static UUID tryUserUuid(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(sub.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
