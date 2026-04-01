package com.nowcoder.community.im.core.security;

import com.nowcoder.community.common.security.jwt.JwtSubjects;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static int userIdOrThrow(Jwt jwt) {
        return JwtSubjects.userIdOrThrow(jwt);
    }
}
