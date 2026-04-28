package com.nowcoder.community.auth.controller.dto;

public class CaptchaIssueResponse {

    private String captchaId;
    private String imageBase64;
    private int ttlSeconds;

    public CaptchaIssueResponse() {
    }

    public CaptchaIssueResponse(String captchaId, String imageBase64, int ttlSeconds) {
        this.captchaId = captchaId;
        this.imageBase64 = imageBase64;
        this.ttlSeconds = ttlSeconds;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
