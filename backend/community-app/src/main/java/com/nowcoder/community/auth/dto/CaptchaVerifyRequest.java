package com.nowcoder.community.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class CaptchaVerifyRequest {

    @NotBlank
    private String captchaId;

    @NotBlank
    private String code;

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
