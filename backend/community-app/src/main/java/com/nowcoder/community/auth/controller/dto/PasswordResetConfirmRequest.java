package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = ValidationLimits.TOKEN_MAX) String resetToken,
        @NotBlank @Size(min = 8, max = ValidationLimits.PASSWORD_MAX) String newPassword,
        @Size(max = ValidationLimits.CAPTCHA_ID_MAX) String captchaId,
        @Size(max = ValidationLimits.CAPTCHA_CODE_MAX) String captchaCode
) {
}
