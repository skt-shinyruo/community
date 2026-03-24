package com.nowcoder.community.auth.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RegisterCodeVerifyRequest {

    @Positive
    private int userId;

    @NotBlank
    @Size(min = ValidationLimits.REGISTRATION_EMAIL_CODE_MIN, max = ValidationLimits.REGISTRATION_EMAIL_CODE_MAX)
    private String code;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
