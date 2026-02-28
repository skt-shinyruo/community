package com.nowcoder.community.auth.service.dto;

public class UserInternalUpdatePasswordRequest {

    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

