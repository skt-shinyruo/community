package com.nowcoder.community.auth.dto;

public class RegisterResponse {

    private int userId;
    /**
     * Opaque token representing a pending registration context.
     * Used by resend/verify endpoints to avoid exposing sequential userId.
     */
    private String registrationToken;
    private boolean emailCodeIssued;
    private String maskedEmail;
    private String debugEmailCode;

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getRegistrationToken() {
        return registrationToken;
    }

    public void setRegistrationToken(String registrationToken) {
        this.registrationToken = registrationToken;
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
