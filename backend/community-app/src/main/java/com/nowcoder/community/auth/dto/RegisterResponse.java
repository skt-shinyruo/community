package com.nowcoder.community.auth.dto;

public class RegisterResponse {

    private int userId;
    private boolean emailCodeIssued;
    private String maskedEmail;
    private String debugEmailCode;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public boolean isEmailCodeIssued() {
        return emailCodeIssued;
    }

    public void setEmailCodeIssued(boolean emailCodeIssued) {
        this.emailCodeIssued = emailCodeIssued;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public void setMaskedEmail(String maskedEmail) {
        this.maskedEmail = maskedEmail;
    }

    public String getDebugEmailCode() {
        return debugEmailCode;
    }

    public void setDebugEmailCode(String debugEmailCode) {
        this.debugEmailCode = debugEmailCode;
    }
}
