package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class PasswordPolicyDomainService {

    private static final int PASSWORD_MIN = 8;
    private static final int BCRYPT_MAX_UTF8_BYTES = 72;

    public String requireValidPassword(String password) {
        String value = password == null ? "" : password;
        if (hasBoundaryWhitespace(value)) {
            throw new BusinessException(INVALID_ARGUMENT, "密码首尾不能包含空白字符");
        }
        if (value.length() < PASSWORD_MIN || value.length() > ValidationLimits.PASSWORD_MAX) {
            throw new BusinessException(INVALID_ARGUMENT, "密码长度必须为 8-" + ValidationLimits.PASSWORD_MAX + " 个字符");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_UTF8_BYTES) {
            throw new BusinessException(INVALID_ARGUMENT, "密码 UTF-8 编码后不能超过 72 字节");
        }
        if (characterClassCount(value) < 2) {
            throw new BusinessException(INVALID_ARGUMENT, "密码至少需要包含两类字符");
        }
        return value;
    }

    private boolean hasBoundaryWhitespace(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return isWhitespace(value.codePointAt(0))
                || isWhitespace(value.codePointBefore(value.length()));
    }

    private boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
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
