package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;

public class AuthDomainService {

    public void requireCredentials(String username, String password) {
        if (!hasText(username) || !hasText(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
