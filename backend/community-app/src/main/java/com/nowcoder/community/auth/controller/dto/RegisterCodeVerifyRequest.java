package com.nowcoder.community.auth.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCodeVerifyRequest(
        @NotBlank @Size(max = ValidationLimits.REGISTRATION_TOKEN_MAX) String registrationToken,
        @NotBlank @Size(
                min = ValidationLimits.REGISTRATION_EMAIL_CODE_MIN,
                max = ValidationLimits.REGISTRATION_CODE_MAX
        ) String code
) {
}
