package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;

public class PasswordResetDomainService {

    public void requireResetRequestEmail(String email) {
        if (!hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
    }

    public void requireConfirmFields(String resetToken, String newPassword) {
        if (!hasText(resetToken) || !hasText(newPassword)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetToken/newPassword 不能为空");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
