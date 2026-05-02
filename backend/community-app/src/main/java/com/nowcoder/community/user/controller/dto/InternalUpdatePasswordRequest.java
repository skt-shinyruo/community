package com.nowcoder.community.user.controller.dto;

import com.nowcoder.community.common.constants.ValidationLimits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InternalUpdatePasswordRequest {

    @NotBlank
    @Size(min = 8, max = ValidationLimits.PASSWORD_MAX)
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
