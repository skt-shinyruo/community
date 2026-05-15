package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserReadDomainService {

    public void assertValidUserId(UUID userId) {
        if (userId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "userId 非法");
        }
    }

    public String normalizeUsername(String username) {
        String value = safeTrim(username);
        if (!hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        return value;
    }

    public String normalizeEmail(String email) {
        String value = safeTrim(email);
        if (!hasText(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        return value;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
