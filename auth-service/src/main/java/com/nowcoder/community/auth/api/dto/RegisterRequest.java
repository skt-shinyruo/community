package com.nowcoder.community.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.validation.ValidationLimits;

public class RegisterRequest {

    @NotBlank
    @Size(max = ValidationLimits.USERNAME_MAX)
    private String username;

    @NotBlank
    @Size(max = ValidationLimits.PASSWORD_MAX)
    private String password;

    @NotBlank
    @Email
    @Size(max = ValidationLimits.EMAIL_MAX)
    private String email;

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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
