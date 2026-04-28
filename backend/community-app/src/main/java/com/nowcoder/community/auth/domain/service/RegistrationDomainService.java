package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.common.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegistrationDomainService {

    public void requireRegisterFields(String username, String password, String email) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(email)) {
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
}
