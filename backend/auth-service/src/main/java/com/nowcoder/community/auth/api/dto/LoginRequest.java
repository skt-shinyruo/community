package com.nowcoder.community.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.platform.validation.ValidationLimits;

public class LoginRequest {

    @NotBlank
    @Size(max = ValidationLimits.USERNAME_MAX)
    private String username;

    @NotBlank
    @Size(max = ValidationLimits.PASSWORD_MAX)
    private String password;

    @Size(max = ValidationLimits.CAPTCHA_ID_MAX)
    private String captchaId;

    @Size(max = ValidationLimits.CAPTCHA_CODE_MAX)
    private String captchaCode;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
