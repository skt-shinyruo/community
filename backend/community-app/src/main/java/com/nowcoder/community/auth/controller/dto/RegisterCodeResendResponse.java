package com.nowcoder.community.auth.controller.dto;

public class RegisterCodeResendResponse {

    private boolean issued;
    private String maskedEmail;
    private String debugEmailCode;

    public boolean isIssued() {
        return issued;
    }

    public void setIssued(boolean issued) {
        this.issued = issued;
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
