package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequestRequest(
        @NotBlank @Email @Size(max = ValidationLimits.EMAIL_MAX) String email,
        @Size(max = ValidationLimits.CAPTCHA_ID_MAX) String captchaId,
        @Size(max = ValidationLimits.CAPTCHA_CODE_MAX) String captchaCode
) {
}
