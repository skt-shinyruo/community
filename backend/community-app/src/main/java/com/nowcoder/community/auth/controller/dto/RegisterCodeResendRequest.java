package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCodeResendRequest(
        @NotBlank @Size(max = ValidationLimits.REGISTRATION_TOKEN_MAX) String registrationToken,
        @Size(max = ValidationLimits.CAPTCHA_ID_MAX) String captchaId,
        @Size(max = ValidationLimits.CAPTCHA_CODE_MAX) String captchaCode
) {
}
