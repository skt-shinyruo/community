package com.nowcoder.community.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nowcoder.community.common.constants.ValidationLimits;

public record LoginRequest(
        @NotBlank @Size(max = ValidationLimits.USERNAME_MAX) String username,
        @NotBlank @Size(max = ValidationLimits.PASSWORD_MAX) String password,
        @Size(max = ValidationLimits.CAPTCHA_ID_MAX) String captchaId,
        @Size(max = ValidationLimits.CAPTCHA_CODE_MAX) String captchaCode
) {
}
