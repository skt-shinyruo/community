package com.nowcoder.community.auth.controller.dto;

public class PasswordResetRequestResponse {

    private boolean issued;
    private String resetLink;

    public PasswordResetRequestResponse() {
    }

    public PasswordResetRequestResponse(boolean issued, String resetLink) {
        this.issued = issued;
        this.resetLink = resetLink;
    }

    public boolean isIssued() {
        return issued;
    }

    public void setIssued(boolean issued) {
        this.issued = issued;
    }

    public String getResetLink() {
        return resetLink;
    }

    public void setResetLink(String resetLink) {
        this.resetLink = resetLink;
    }
}
