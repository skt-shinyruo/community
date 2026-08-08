package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;

public class AuthDomainService {

    public String requireCredentials(String username, String password) {
        if (!hasText(username) || !hasText(password)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (containsUnsafeLoginCharacter(username)) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        return username.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean containsUnsafeLoginCharacter(String value) {
        return value.codePoints().anyMatch(this::isUnsafeLoginCodePoint);
    }

    private boolean isUnsafeLoginCodePoint(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.CONTROL || type == Character.FORMAT || type == Character.SURROGATE) {
            return true;
        }
        return codePoint == 0x034F
                || between(codePoint, 0x115F, 0x1160)
                || between(codePoint, 0x17B4, 0x17B5)
                || between(codePoint, 0x180B, 0x180F)
                || codePoint == 0x3164
                || between(codePoint, 0xFE00, 0xFE0F)
                || codePoint == 0xFFA0
                || between(codePoint, 0x1BCA0, 0x1BCA3)
                || between(codePoint, 0x1D173, 0x1D17A)
                || between(codePoint, 0xE0000, 0xE0FFF);
    }

    private boolean between(int value, int lowerInclusive, int upperInclusive) {
        return value >= lowerInclusive && value <= upperInclusive;
    }
}
