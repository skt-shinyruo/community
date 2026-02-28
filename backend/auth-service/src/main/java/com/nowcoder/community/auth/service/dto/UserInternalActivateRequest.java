package com.nowcoder.community.auth.service.dto;

public class UserInternalActivateRequest {

    private String activationCode;

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
}

