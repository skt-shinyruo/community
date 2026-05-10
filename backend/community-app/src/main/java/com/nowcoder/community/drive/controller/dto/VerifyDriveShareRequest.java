package com.nowcoder.community.drive.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyDriveShareRequest {

    @NotBlank
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
