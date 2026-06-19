package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterCodeVerifyRequest {

    @NotBlank
    @Size(max = ValidationLimits.REGISTRATION_TOKEN_MAX)
    private String registrationToken;

    @NotBlank
    @Size(min = ValidationLimits.REGISTRATION_EMAIL_CODE_MIN, max = ValidationLimits.REGISTRATION_EMAIL_CODE_MAX)
    private String code;

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
