package com.nowcoder.community.infra.security.auth;

import com.nowcoder.community.contracts.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import static com.nowcoder.community.contracts.api.CommonErrorCode.INVALID_ARGUMENT;
import static com.nowcoder.community.contracts.api.CommonErrorCode.UNAUTHORIZED;

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

    /**
     * Parse {@code jwt.subject} as integer userId.
     *
     * @throws BusinessException UNAUTHORIZED if authentication/principal missing
     * @throws BusinessException INVALID_ARGUMENT if subject missing/invalid
     */
    public static int requireUserId(Authentication authentication) {
        Jwt jwt = requireJwt(authentication);
        String sub = jwt.getSubject();
        if (!StringUtils.hasText(sub)) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
        try {
            int userId = Integer.parseInt(sub);
            if (userId <= 0) {
                throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
            }
            return userId;
        } catch (NumberFormatException e) {
            throw new BusinessException(INVALID_ARGUMENT, "token subject 非法");
        }
    }

    /**
     * Best-effort userId parse.
     *
     * @return userId when present and &gt; 0, otherwise null
     */
    public static Integer tryUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return null;
        }
        String sub = jwt.getSubject();
        if (!StringUtils.hasText(sub)) {
            return null;
        }
        try {
            int userId = Integer.parseInt(sub);
            return userId > 0 ? userId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

