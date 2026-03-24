package com.nowcoder.community.auth.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RegisterCodeResendRequest {

    @Positive
    private int userId;

    @Size(max = ValidationLimits.CAPTCHA_ID_MAX)
    private String captchaId;

    @Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
    private String captchaCode;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }
}
