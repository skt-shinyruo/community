package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetConfirmRequest {

    @NotBlank
    @Size(max = ValidationLimits.TOKEN_MAX)
    private String resetToken;

    @NotBlank
    @Size(min = 8, max = ValidationLimits.PASSWORD_MAX)
    private String newPassword;

    @Size(max = ValidationLimits.CAPTCHA_ID_MAX)
    private String captchaId;

    @Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
    private String captchaCode;

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
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
