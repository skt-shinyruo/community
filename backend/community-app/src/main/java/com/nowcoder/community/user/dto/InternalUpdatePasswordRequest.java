package com.nowcoder.community.user.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalUpdatePasswordRequest {

    @NotBlank
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

