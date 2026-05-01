package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;

public class CaptchaDomainService {

    public void requireCaptcha(String captchaId, String code) {
        if (!hasText(captchaId) || !hasText(code)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
    }

    public String normalizeCode(String code) {
        if (!hasText(code)) {
            return "";
        }
        return code.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
