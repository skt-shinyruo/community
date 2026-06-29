package com.nowcoder.community.auth.controller.dto;

public class PasswordResetRequestResponse {

    private boolean issued;

    public PasswordResetRequestResponse() {
    }

    public PasswordResetRequestResponse(boolean issued) {
        this.issued = issued;
    }

    public boolean isIssued() {
        return issued;
    }

    public void setIssued(boolean issued) {
        this.issued = issued;
    }
}
