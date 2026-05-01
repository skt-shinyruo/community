package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;

public class RegistrationDomainService {

    public void requireRegisterFields(String username, String password, String email) {
        if (!hasText(username) || !hasText(password) || !hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "用户名/密码/邮箱不能为空");
        }
    }

    public String maskEmail(String email) {
        String normalized = email == null ? "" : email.trim();
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return normalized;
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        if (local.length() == 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
