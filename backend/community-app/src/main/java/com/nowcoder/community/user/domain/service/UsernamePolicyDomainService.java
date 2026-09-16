package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.text.UnsafeCodePoints;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

/**
 * Owner-domain policy for usernames. The same boundary is applied when a
 * credential is created and when an existing credential is resolved, so a
 * username cannot be registered into a state that the login path rejects.
 */
public class UsernamePolicyDomainService {

    public String requireValid(String username) {
        if (username == null || username.isBlank() || containsUnsafeCharacter(username)) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名格式非法");
        }
        String value = trim(username);
        if (value.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "用户名格式非法");
        }
        return value;
    }

    public boolean isSafe(String username) {
        return username != null
                && !username.isBlank()
                && !containsUnsafeCharacter(username)
                && !trim(username).isEmpty();
    }

    public String trim(String username) {
        return username == null ? "" : username.trim();
    }

    private boolean containsUnsafeCharacter(String value) {
        return UnsafeCodePoints.containsUnsafe(value);
    }
}
