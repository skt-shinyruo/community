package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PasswordResetDomainService {

    public void requireResetRequestEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "email 不能为空");
        }
    }

    public void requireConfirmFields(String resetToken, String newPassword) {
        if (!StringUtils.hasText(resetToken) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT, "resetToken/newPassword 不能为空");
        }
    }
}
