package com.nowcoder.community.auth.domain.service;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaDomainService {

    public void requireCaptcha(String captchaId, String code) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(code)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
    }

    public String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim();
    }
}
