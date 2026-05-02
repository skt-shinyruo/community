package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class PasswordPolicyDomainService {

    private static final int PASSWORD_MIN = 8;

    public String requireValidPassword(String password) {
        String trimmed = password == null ? "" : password.trim();
        if (trimmed.length() < PASSWORD_MIN || trimmed.length() > ValidationLimits.PASSWORD_MAX) {
            throw new BusinessException(INVALID_ARGUMENT, "密码长度必须为 8-" + ValidationLimits.PASSWORD_MAX + " 个字符");
        }
        if (characterClassCount(trimmed) < 2) {
            throw new BusinessException(INVALID_ARGUMENT, "密码至少需要包含两类字符");
        }
        return trimmed;
    }

    private int characterClassCount(String password) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (Character.isLowerCase(ch)) {
                lower = true;
            } else if (Character.isUpperCase(ch)) {
                upper = true;
            } else if (Character.isDigit(ch)) {
                digit = true;
            } else {
                symbol = true;
            }
        }
        return (lower ? 1 : 0) + (upper ? 1 : 0) + (digit ? 1 : 0) + (symbol ? 1 : 0);
    }
}
