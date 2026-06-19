package com.nowcoder.community.auth.application;

import com.nowcoder.community.auth.exception.AuthErrorCode;
import com.nowcoder.community.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CaptchaChallengeComponent {

    private final CaptchaApplicationService captchaApplicationService;

    public CaptchaChallengeComponent(CaptchaApplicationService captchaApplicationService) {
        this.captchaApplicationService = captchaApplicationService;
    }

    public void requireValidCaptcha(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_REQUIRED);
        }
        if (!verify(captchaId, captchaCode)) {
            throw new BusinessException(AuthErrorCode.CAPTCHA_INVALID);
        }
    }

    public boolean verify(String captchaId, String captchaCode) {
        return captchaApplicationService.verify(captchaId, captchaCode);
    }
}
