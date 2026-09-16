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
        return EmailMasking.maskEmail(email);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
