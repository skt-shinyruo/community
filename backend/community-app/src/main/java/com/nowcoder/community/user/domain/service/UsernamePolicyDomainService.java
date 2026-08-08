package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

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
        return value.codePoints().anyMatch(this::isUnsafeCodePoint);
    }

    private boolean isUnsafeCodePoint(int codePoint) {
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
