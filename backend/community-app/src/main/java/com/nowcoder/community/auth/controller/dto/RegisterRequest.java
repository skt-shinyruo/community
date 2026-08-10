package com.nowcoder.community.auth.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

public record RegisterRequest(
        @NotBlank @Size(max = ValidationLimits.USERNAME_MAX) String username,
        @NotBlank @Size(min = 8, max = ValidationLimits.PASSWORD_MAX) String password,
        @NotBlank @Email @Size(max = ValidationLimits.EMAIL_MAX) String email,
        @Size(max = ValidationLimits.CAPTCHA_ID_MAX) String captchaId,
        @Size(max = ValidationLimits.CAPTCHA_CODE_MAX) String captchaCode
) {
}
