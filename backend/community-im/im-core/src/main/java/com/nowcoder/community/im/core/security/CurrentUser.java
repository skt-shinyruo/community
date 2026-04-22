package com.nowcoder.community.im.core.security;

import com.nowcoder.community.common.security.jwt.JwtSubjects;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID userIdOrThrow(Jwt jwt) {
        return JwtSubjects.userUuidOrThrow(jwt);
    }
}
