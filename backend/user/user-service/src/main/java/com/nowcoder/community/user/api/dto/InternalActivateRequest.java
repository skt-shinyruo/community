package com.nowcoder.community.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public class InternalActivateRequest {

    @NotBlank
    private String activationCode;

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
}

