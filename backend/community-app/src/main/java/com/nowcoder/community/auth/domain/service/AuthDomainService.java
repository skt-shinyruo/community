package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.text.UnsafeCodePoints;

public class AuthDomainService {

    public String requireCredentials(String username, String password) {
        if (!hasText(username) || !hasText(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (UnsafeCodePoints.containsUnsafe(username)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        return username.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
