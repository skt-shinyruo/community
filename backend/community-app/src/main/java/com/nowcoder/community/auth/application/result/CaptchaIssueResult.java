package com.nowcoder.community.auth.application.result;

public record CaptchaIssueResult(String captchaId, String imageBase64, int ttlSeconds) {
}
