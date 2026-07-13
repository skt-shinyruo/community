package com.nowcoder.community.infra.security.auth;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.security.jwt.JwtSubjects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.common.exception.CommonErrorCode.UNAUTHORIZED;

/**
 * Current user helpers (JWT resource server).
 *
 * <p>Centralizes {@link Authentication} / {@link Jwt} parsing to avoid duplicated controller logic across modules.</p>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @throws BusinessException UNAUTHORIZED if authentication/principal missing
     */
    public static Jwt requireJwt(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        throw new BusinessException(UNAUTHORIZED, "未获取到认证信息");
    }

    public static UUID requireUserUuid(Authentication authentication) {
        try {
            return JwtSubjects.userUuidOrThrow(requireJwt(authentication));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }

    public static UUID tryUserUuid(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return null;
        }
        return JwtSubjects.tryUserUuid(jwt);
    }
}
