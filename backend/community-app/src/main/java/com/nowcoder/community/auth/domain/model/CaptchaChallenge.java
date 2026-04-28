package com.nowcoder.community.auth.domain.model;

public record CaptchaChallenge(String captchaId, String code, int ttlSeconds) {
}
